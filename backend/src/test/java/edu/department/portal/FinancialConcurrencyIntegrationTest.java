package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "track2.test.jdbcUrl", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
        "spring.datasource.url=${track2.test.jdbcUrl}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${track2.test.username}",
        "spring.datasource.password=${track2.test.password:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({Api.class, AuditService.class, StudentPortalService.class,
        UserManagementService.class, FinancialConcurrencyIntegrationTest.PasswordConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FinancialConcurrencyIntegrationTest {
    private static final int REQUESTS_PER_ROUND = 4;
    private static final int ROUNDS = 10;

    @Autowired private Api api;
    @Autowired private StudentRepo students;
    @Autowired private LedgerRepo ledger;
    @Autowired private FundRepo funds;
    @Autowired private EventRepo events;
    @Autowired private AttendanceRepo attendance;
    @Autowired private UserRepo users;
    @Autowired private UserManagementService userManagement;
    @Autowired private AuditService audit;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void ledgerAndAuditWritesRollBackTogether() {
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");
        long fundAuditCountBefore = audit.recent().stream()
                .filter(entry -> "FUND_ENTRY_CREATED".equals(entry.action))
                .count();
        Student student = new Student();
        student.studentNumber = "ROLLBACK-" + UUID.randomUUID();
        student.fullName = "Transaction rollback test";
        student = students.saveAndFlush(student);
        String reference = "ROLLBACK-" + UUID.randomUUID();
        Student target = student;

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            api.addLedger(new Api.LedgerInput(target.id, LedgerEntry.Kind.FINE,
                    new BigDecimal("25.00"), "Rollback test fine", reference), auth);
            throw new IllegalStateException("Force transaction rollback.");
        }));

        assertTrue(ledger.findByStudentIdOrderByCreatedAtDesc(target.id).isEmpty());
        assertTrue(audit.recent().stream()
                .noneMatch(entry -> entry.details != null && entry.details.contains(reference)));

        FundEntry addition = new FundEntry();
        addition.type = FundEntry.Type.ADDITION;
        addition.amount = new BigDecimal("25.00");
        addition.description = "Rollback fund " + UUID.randomUUID();
        String fundDescription = addition.description;
        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            api.addFund(addition, auth);
            throw new IllegalStateException("Force fund transaction rollback.");
        }));
        assertTrue(funds.findAll().stream().noneMatch(entry -> entry.description.equals(fundDescription)));
        assertEquals(fundAuditCountBefore, audit.recent().stream()
                .filter(entry -> "FUND_ENTRY_CREATED".equals(entry.action))
                .count());
    }

    @Test
    void fundLockSerializesTransactionsWhenLedgerStartsEmpty() throws Exception {
        funds.deleteAllInBatch();
        funds.flush();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch secondAttemptStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionCompleted = new CountDownLatch(1);
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");
        try {
            Future<?> openingBalance = executor.submit(() -> inTransaction(() -> {
                assertEquals(1, funds.acquireFundLedgerLock());
                assertTrue(funds.findAllForUpdate().isEmpty());
                firstLockAcquired.countDown();
                awaitLatch(releaseFirstTransaction);
                FundEntry opening = new FundEntry();
                opening.type = FundEntry.Type.OPENING_BALANCE;
                opening.amount = new BigDecimal("1000.00");
                opening.description = "Empty-ledger opening balance";
                api.addFund(opening, auth);
                return null;
            }));

            assertTrue(firstLockAcquired.await(10, TimeUnit.SECONDS));
            Future<?> expense = executor.submit(() -> {
                secondAttemptStarted.countDown();
                try {
                    return inTransaction(() -> {
                        FundEntry disbursement = new FundEntry();
                        disbursement.type = FundEntry.Type.DISBURSEMENT;
                        disbursement.amount = new BigDecimal("600.00");
                        disbursement.description = "Empty-ledger concurrent expense";
                        return api.addFund(disbursement, auth);
                    });
                } finally {
                    secondTransactionCompleted.countDown();
                }
            });

            assertTrue(secondAttemptStarted.await(10, TimeUnit.SECONDS));
            assertFalse(secondTransactionCompleted.await(200, TimeUnit.MILLISECONDS));
            releaseFirstTransaction.countDown();
            openingBalance.get(10, TimeUnit.SECONDS);
            expense.get(10, TimeUnit.SECONDS);

            assertEquals(new BigDecimal("400.00"), funds.findAll().stream()
                    .map(entry -> entry.type == FundEntry.Type.DISBURSEMENT
                            ? entry.amount.negate() : entry.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentPaymentsAndExpensesCannotExceedAvailableBalances() throws Exception {
        funds.deleteAllInBatch();
        funds.flush();
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS_PER_ROUND);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Student newStudent = new Student();
                newStudent.studentNumber = "CONC-" + UUID.randomUUID();
                newStudent.fullName = "Concurrency test";
                Student student = students.saveAndFlush(newStudent);

                LedgerEntry fine = new LedgerEntry();
                fine.student = student;
                fine.kind = LedgerEntry.Kind.FINE;
                fine.amount = new BigDecimal("100.00");
                fine.description = "Concurrency test fine";
                ledger.saveAndFlush(fine);

                assertEquals(1, runTogether(executor, REQUESTS_PER_ROUND, index -> {
                    try {
                        inTransaction(() -> api.addLedger(new Api.LedgerInput(student.id, LedgerEntry.Kind.PAYMENT,
                                new BigDecimal("60.00"), "Concurrent payment", "PAY-" + UUID.randomUUID()), auth));
                        return true;
                    } catch (ResponseStatusException failure) {
                        assertEquals(400, failure.getStatusCode().value());
                        return false;
                    }
                }));
                List<LedgerEntry> studentEntries = ledger.findByStudentIdOrderByCreatedAtDesc(student.id);
                assertEquals(new BigDecimal("40.00"), studentEntries.stream()
                        .map(entry -> entry.kind == LedgerEntry.Kind.FINE
                                ? entry.amount : entry.amount.negate())
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                LedgerEntry acceptedPayment = studentEntries.stream()
                        .filter(entry -> entry.kind == LedgerEntry.Kind.PAYMENT)
                        .findFirst()
                        .orElseThrow();
                assertEquals(1, audit.recent().stream()
                        .filter(entry -> "LEDGER_ENTRY_CREATED".equals(entry.action))
                        .filter(entry -> "LedgerEntry".equals(entry.entityType))
                        .filter(entry -> acceptedPayment.id.toString().equals(entry.entityId))
                        .count());
            }

            FundEntry openingBalance = new FundEntry();
            openingBalance.type = FundEntry.Type.OPENING_BALANCE;
            openingBalance.amount = new BigDecimal("100.00");
            openingBalance.description = "Concurrency test opening balance";
            inTransaction(() -> api.addFund(openingBalance, auth));

            for (int round = 0; round < ROUNDS; round++) {
                assertEquals(1, runTogether(executor, REQUESTS_PER_ROUND, index -> {
                    FundEntry expense = new FundEntry();
                    expense.type = FundEntry.Type.DISBURSEMENT;
                    expense.amount = new BigDecimal("60.00");
                    expense.description = "Concurrent test expense";
                    try {
                        inTransaction(() -> api.addFund(expense, auth));
                        return true;
                    } catch (ResponseStatusException failure) {
                        assertEquals(400, failure.getStatusCode().value());
                        return false;
                    }
                }));
                assertEquals(new BigDecimal("40.00"), funds.findAll().stream()
                        .map(entry -> entry.type == FundEntry.Type.DISBURSEMENT
                                ? entry.amount.negate() : entry.amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

                if (round + 1 < ROUNDS) {
                    FundEntry addition = new FundEntry();
                    addition.type = FundEntry.Type.ADDITION;
                    addition.amount = new BigDecimal("60.00");
                    addition.description = "Concurrency test replenishment";
                    inTransaction(() -> api.addFund(addition, auth));
                }
            }

            Student scanStudent = new Student();
            scanStudent.studentNumber = "SCAN-" + UUID.randomUUID();
            scanStudent.fullName = "Concurrency test";
            scanStudent = students.saveAndFlush(scanStudent);
            DepartmentEvent event = new DepartmentEvent();
            event.title = "Concurrency test";
            event.eventDate = java.time.LocalDate.now();
            event = events.saveAndFlush(event);
            Student finalScanStudent = scanStudent;
            DepartmentEvent finalEvent = event;
            assertEquals(1, runTogether(executor, REQUESTS_PER_ROUND, index -> {
                try {
                    inTransaction(() -> api.manualAttendance(new Api.ScanInput(finalEvent.id,
                            finalScanStudent.studentNumber, Attendance.Status.PRESENT), auth));
                    return true;
                } catch (ResponseStatusException failure) {
                    assertEquals(409, failure.getStatusCode().value());
                    return false;
                }
            }));
            assertEquals(1, attendance.findByEventId(event.id).size());

            UserAccount firstAccount = newStudentAccount("LINK-" + UUID.randomUUID());
            UserAccount secondAccount = newStudentAccount("LINK-" + UUID.randomUUID());
            firstAccount = users.saveAndFlush(firstAccount);
            secondAccount = users.saveAndFlush(secondAccount);
            UserAccount finalFirstAccount = firstAccount;
            UserAccount finalSecondAccount = secondAccount;
            assertEquals(1, runTogether(executor, 2, index -> {
                UserAccount account = index % 2 == 0 ? finalFirstAccount : finalSecondAccount;
                try {
                    inTransaction(() -> userManagement.linkStudent(account.id,
                            finalScanStudent.id, "admin"));
                    return true;
                } catch (ResponseStatusException failure) {
                    assertEquals(409, failure.getStatusCode().value());
                    return false;
                }
            }));
            assertEquals(1, users.findAllByOrderByUsernameAsc().stream()
                    .filter(account -> account.student != null && account.student.id.equals(finalScanStudent.id))
                    .count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private UserAccount newStudentAccount(String username) {
        UserAccount account = new UserAccount();
        account.username = username;
        account.passwordHash = "$2a$10$placeholder";
        account.role = UserAccount.UserRole.STUDENT;
        account.enabled = true;
        return account;
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the concurrent fund transaction.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the fund transaction.", exception);
        }
    }

    private int runTogether(ExecutorService executor, int requestCount, Payment payment) throws Exception {
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            int requestIndex = i;
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start.");
                }
                return payment.execute(requestIndex);
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        int accepted = 0;
        for (Future<Boolean> result : results) {
            if (result.get(10, TimeUnit.SECONDS)) accepted++;
        }
        return accepted;
    }

    @FunctionalInterface
    private interface Payment {
        boolean execute(int index);
    }

    @TestConfiguration
    static class PasswordConfig {
        @Bean
        PasswordEncoder concurrencyTestPasswordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
