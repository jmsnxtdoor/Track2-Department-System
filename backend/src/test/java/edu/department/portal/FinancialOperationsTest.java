package edu.department.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinancialOperationsTest {
    private StudentRepo students;
    private LedgerRepo ledger;
    private FundRepo funds;
    private EventRepo events;
    private AttendanceRepo attendance;
    private AuditService audit;
    private Api api;
    private Student student;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepo.class);
        ledger = mock(LedgerRepo.class);
        funds = mock(FundRepo.class);
        events = mock(EventRepo.class);
        attendance = mock(AttendanceRepo.class);
        audit = mock(AuditService.class);
        api = new Api(students, ledger, funds, events, attendance, audit, mock(StudentPortalService.class));
        student = new Student();
        student.id = 10L;
        student.studentNumber = "S-10";
        when(students.findById(10L)).thenReturn(Optional.of(student));
        when(students.findByIdForUpdate(10L)).thenReturn(Optional.of(student));
    }

    @Test
    void acceptsPartialPaymentAndRejectsPaymentAboveRemainingBalanceUnderStudentLock() {
        LedgerEntry fine = new LedgerEntry();
        fine.kind = LedgerEntry.Kind.FINE;
        fine.amount = new BigDecimal("100.00");
        List<LedgerEntry> currentEntries = new ArrayList<>(List.of(fine));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(currentEntries);
        when(ledger.existsByReferenceNumber(any())).thenReturn(false);
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 11L;
            currentEntries.add(saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");

        LedgerEntry payment = api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.PAYMENT,
                new BigDecimal("40.00"), "Partial payment", "PAY-1"), auth);

        assertEquals(LedgerEntry.Kind.PAYMENT, payment.kind);
        assertEquals(new BigDecimal("40.00"), payment.amount);
        assertThrows(ResponseStatusException.class, () -> api.addLedger(
                new Api.LedgerInput(10L, LedgerEntry.Kind.PAYMENT,
                        new BigDecimal("70.00"), "Overpayment", "PAY-2"), auth));
        verify(ledger, times(1)).save(any(LedgerEntry.class));
        verify(students, times(2)).findByIdForUpdate(10L);
    }

    @Test
    void createsFineWithNormalizedDescriptionAndReference() {
        when(ledger.existsByReferenceNumber("FINE-1")).thenReturn(false);
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 12L;
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");

        LedgerEntry fine = api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                new BigDecimal("125.50"), "  Laboratory fee  ", "  FINE-1  "), auth);

        assertEquals(12L, fine.id);
        assertEquals(new BigDecimal("125.50"), fine.amount);
        assertEquals("Laboratory fee", fine.description);
        assertEquals("FINE-1", fine.referenceNumber);
        verify(ledger).save(fine);
    }

    @Test
    void rejectsDuplicateTransactionReferenceWithoutSaving() {
        when(ledger.existsByReferenceNumber("FINE-1")).thenReturn(true);
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                        new BigDecimal("50.00"), "Duplicate fine", " FINE-1 "), auth));

        assertEquals(409, failure.getStatusCode().value());
        verify(ledger, never()).save(any(LedgerEntry.class));
    }

    @Test
    void retriesLedgerRequestWithoutReferenceReturnOriginalAndDoNotDuplicateAudit() {
        ConcurrentHashMap<String, LedgerEntry> savedByKey = new ConcurrentHashMap<>();
        when(ledger.findByIdempotencyKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedByKey.get(invocation.getArgument(0))));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 12L;
            savedByKey.put(saved.idempotencyKey, saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");
        Api.LedgerInput input = new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                new BigDecimal("50.00"), "Retry fine", null, "retry-ledger");

        LedgerEntry first = api.addLedger(input, auth);
        LedgerEntry retry = api.addLedger(input, auth);

        assertSame(first, retry);
        assertEquals(1, savedByKey.size());
        verify(ledger, times(1)).save(any(LedgerEntry.class));
        verify(audit, times(1)).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsReusingLedgerKeyWithDifferentDetailsAndAllowsSameAmountWithNewKey() {
        ConcurrentHashMap<String, LedgerEntry> savedByKey = new ConcurrentHashMap<>();
        when(ledger.findByIdempotencyKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedByKey.get(invocation.getArgument(0))));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = (long) savedByKey.size() + 1;
            savedByKey.put(saved.idempotencyKey, saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");

        api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                new BigDecimal("50.00"), "First fine", null, "same-key"), auth);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                        new BigDecimal("50.00"), "Different fine", null, "same-key"), auth));
        api.addLedger(new Api.LedgerInput(10L, LedgerEntry.Kind.FINE,
                new BigDecimal("50.00"), "Second fine", null, "separate-key"), auth);

        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("Idempotency key was already used with different transaction details.",
                conflict.getReason());
        assertEquals(2, savedByKey.size());
        verify(ledger, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void fundRetriesReturnOriginalAndDistinctKeysPermitSameAmount() {
        ConcurrentHashMap<String, FundEntry> savedByKey = new ConcurrentHashMap<>();
        List<FundEntry> entries = new ArrayList<>();
        when(funds.findByIdempotencyKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedByKey.get(invocation.getArgument(0))));
        when(funds.findAll()).thenReturn(entries);
        when(funds.findAllForUpdate()).thenReturn(entries);
        when(students.findAll()).thenReturn(List.of());
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(events.findByActiveTrue()).thenReturn(List.of());
        when(attendance.count()).thenReturn(0L);
        when(attendance.findAll()).thenReturn(List.of());
        when(funds.save(any(FundEntry.class))).thenAnswer(invocation -> {
            FundEntry saved = invocation.getArgument(0);
            saved.id = (long) entries.size() + 1;
            entries.add(saved);
            savedByKey.put(saved.idempotencyKey, saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");
        Api.FundInput firstInput = new Api.FundInput(FundEntry.Type.ADDITION,
                new BigDecimal("25.00"), "Donation", "fund-retry");

        FundEntry first = api.addFund(firstInput, auth);
        FundEntry retry = api.addFund(firstInput, auth);
        FundEntry separate = api.addFund(new Api.FundInput(FundEntry.Type.ADDITION,
                new BigDecimal("25.00"), "Second donation", "fund-separate"), auth);

        assertSame(first, retry);
        assertNotEquals(first.id, separate.id);
        assertEquals(new BigDecimal("50.00"), api.dashboard().get("fundBalance"));
        assertEquals(2, entries.size());
        verify(audit, times(2)).record(any(), any(), any(), any(), any());
    }

    @Test
    void reversesLedgerEntryWithoutChangingOriginalAndRefusesSecondReversal() {
        LedgerEntry original = new LedgerEntry();
        original.id = 25L;
        original.student = student;
        original.kind = LedgerEntry.Kind.FINE;
        original.amount = new BigDecimal("75.00");
        original.description = "Original fine";
        when(ledger.findById(25L)).thenReturn(Optional.of(original));
        when(students.findByIdForUpdate(10L)).thenReturn(Optional.of(student));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(original));
        when(ledger.existsByDescriptionStartingWith("REVERSAL:FINE:25:")).thenReturn(false, true);
        ConcurrentHashMap<String, LedgerEntry> savedByKey = new ConcurrentHashMap<>();
        when(ledger.findByIdempotencyKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedByKey.get(invocation.getArgument(0))));
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 26L;
            savedByKey.put(saved.idempotencyKey, saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a");
        Api.ReasonInput reason = new Api.ReasonInput("Entered in error", "fine-reversal-key");

        LedgerEntry reversal = api.reverseLedger(25L, reason, auth);
        LedgerEntry retry = api.reverseLedger(25L, reason, auth);

        assertEquals(LedgerEntry.Kind.PAYMENT, reversal.kind);
        assertSame(reversal, retry);
        assertEquals(original.amount, reversal.amount);
        assertTrue(reversal.description.startsWith("REVERSAL:FINE:25:"));
        assertEquals("Original fine", original.description);
        assertThrows(ResponseStatusException.class,
                () -> api.reverseLedger(25L,
                        new Api.ReasonInput("Different attempt", "different-reversal-key"), auth));
        verify(students, times(2)).findByIdForUpdate(10L);
        verify(ledger, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    void reversesPaymentByRestoringTheFineAndReducingNetCollections() {
        LedgerEntry payment = new LedgerEntry();
        payment.id = 30L;
        payment.student = student;
        payment.kind = LedgerEntry.Kind.PAYMENT;
        payment.amount = new BigDecimal("40.00");
        payment.description = "Partial payment";
        List<LedgerEntry> entries = new ArrayList<>(List.of(payment));
        when(ledger.findById(30L)).thenReturn(Optional.of(payment));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(entries);
        when(ledger.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation -> entries);
        when(ledger.existsByDescriptionStartingWith("REVERSAL:PAYMENT:30:")).thenReturn(false);
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 31L;
            entries.add(saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a");

        LedgerEntry reversal = api.reverseLedger(30L, new Api.ReasonInput("  Payment returned  "), auth);

        assertEquals(LedgerEntry.Kind.FINE, reversal.kind);
        assertEquals(new BigDecimal("40.00"), reversal.amount);
        assertEquals("REVERSAL:PAYMENT:30:Payment returned", reversal.description);
        assertEquals(new BigDecimal("0.00"), api.dashboard().get("collections"));
        assertEquals("Partial payment", payment.description);
        assertEquals(2, entries.size());
    }

    @Test
    void refusesToReverseFineWhenAnyPartHasBeenPaid() {
        LedgerEntry fine = new LedgerEntry();
        fine.id = 32L;
        fine.student = student;
        fine.kind = LedgerEntry.Kind.FINE;
        fine.amount = new BigDecimal("100.00");
        fine.description = "Original fine";
        LedgerEntry payment = new LedgerEntry();
        payment.student = student;
        payment.kind = LedgerEntry.Kind.PAYMENT;
        payment.amount = new BigDecimal("25.00");
        payment.description = "Partial payment";
        when(ledger.findById(32L)).thenReturn(Optional.of(fine));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(fine, payment));
        when(ledger.existsByDescriptionStartingWith("REVERSAL:FINE:32:")).thenReturn(false);
        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> api.reverseLedger(32L, new Api.ReasonInput("Entered in error"), auth));

        assertEquals(400, failure.getStatusCode().value());
        verify(ledger, never()).save(any(LedgerEntry.class));
    }

    @Test
    void fundBalanceIncludesOpeningBalanceAndExpensesAndRejectsOverspending() {
        List<FundEntry> entries = new ArrayList<>();
        when(funds.findAll()).thenAnswer(invocation -> entries);
        when(funds.findAllForUpdate()).thenAnswer(invocation -> entries);
        when(funds.save(any(FundEntry.class))).thenAnswer(invocation -> {
            FundEntry saved = invocation.getArgument(0);
            saved.id = (long) entries.size() + 1;
            entries.add(saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("treasurer", "n/a");

        addFundEntry(fund(FundEntry.Type.OPENING_BALANCE, "1000.00", "Opening funds"), auth);
        addFundEntry(fund(FundEntry.Type.DISBURSEMENT, "275.50", "Office supplies"), auth);

        assertEquals(new BigDecimal("724.50"), api.dashboard().get("fundBalance"));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> addFundEntry(fund(FundEntry.Type.DISBURSEMENT, "724.51", "Over budget"), auth));
        assertEquals(400, failure.getStatusCode().value());
        assertEquals(2, entries.size());
        verify(funds, times(3)).acquireFundLedgerLock();
    }

    @Test
    void preventsReversingSpentFundAdditionAndRejectsDuplicateFundReversal() {
        List<FundEntry> entries = new ArrayList<>();
        FundEntry opening = fund(FundEntry.Type.OPENING_BALANCE, "1000.00", "Opening funds");
        opening.id = 40L;
        FundEntry expense = fund(FundEntry.Type.DISBURSEMENT, "600.00", "Equipment");
        expense.id = 41L;
        entries.addAll(List.of(opening, expense));
        when(funds.findAll()).thenAnswer(invocation -> entries);
        when(funds.findAllForUpdate()).thenAnswer(invocation -> entries);
        when(funds.findById(40L)).thenReturn(Optional.of(opening));
        when(funds.findById(41L)).thenReturn(Optional.of(expense));
        when(funds.existsByDescriptionStartingWith("REVERSAL:OPENING_BALANCE:40:")).thenReturn(false);
        when(funds.existsByDescriptionStartingWith("REVERSAL:DISBURSEMENT:41:")).thenReturn(false, true);
        ConcurrentHashMap<String, FundEntry> savedByKey = new ConcurrentHashMap<>();
        when(funds.findByIdempotencyKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedByKey.get(invocation.getArgument(0))));
        when(funds.save(any(FundEntry.class))).thenAnswer(invocation -> {
            FundEntry saved = invocation.getArgument(0);
            saved.id = (long) entries.size() + 40;
            entries.add(saved);
            savedByKey.put(saved.idempotencyKey, saved);
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a");

        ResponseStatusException spent = assertThrows(ResponseStatusException.class,
                () -> api.reverseFund(40L, new Api.ReasonInput("Remove opening balance"), auth));
        assertEquals(400, spent.getStatusCode().value());

        Api.ReasonInput reason = new Api.ReasonInput("Expense correction", "fund-reversal-key");
        FundEntry reversal = api.reverseFund(41L, reason, auth);
        FundEntry retry = api.reverseFund(41L, reason, auth);
        assertSame(reversal, retry);
        assertEquals(FundEntry.Type.ADDITION, reversal.type);
        assertEquals(new BigDecimal("1000.00"), api.dashboard().get("fundBalance"));

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> api.reverseFund(41L,
                        new Api.ReasonInput("Duplicate correction", "another-fund-reversal-key"), auth));
        assertEquals(409, duplicate.getStatusCode().value());
        verify(funds, times(1)).save(any(FundEntry.class));
    }

    @Test
    void financeReportBalancesAndCollectionsMatchTheLedger() {
        LedgerEntry fine = entry(50L, LedgerEntry.Kind.FINE, "100.00", "Fine");
        fine.student = student;
        LedgerEntry payment = entry(51L, LedgerEntry.Kind.PAYMENT, "35.00", "Payment");
        payment.student = student;
        student.studentNumber = "S-10";
        student.fullName = "Fictional Finance Student";
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(payment, fine));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(payment, fine));
        when(students.findAllByOrderByStudentNumberAsc()).thenReturn(List.of(student));

        var report = api.financeReport();

        assertEquals(new BigDecimal("35.00"), report.get("totalCollections"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> balances =
                (List<java.util.Map<String, Object>>) report.get("balances");
        assertEquals(1, balances.size());
        assertEquals(new BigDecimal("65.00"), balances.get(0).get("unpaidBalance"));
        assertEquals(2, ((List<?>) report.get("transactions")).size());
    }

    @Test
    void dashboardUsesStoredRowsForCollectionAndAttendanceRate() {
        LedgerEntry payment = new LedgerEntry();
        payment.id = 2L;
        payment.kind = LedgerEntry.Kind.PAYMENT;
        payment.amount = new BigDecimal("40.00");
        payment.description = "Payment";
        payment.createdAt = LocalDateTime.now();
        LedgerEntry paymentReversal = new LedgerEntry();
        paymentReversal.kind = LedgerEntry.Kind.FINE;
        paymentReversal.amount = new BigDecimal("5.00");
        paymentReversal.description = "REVERSAL:PAYMENT:2:entered in error";
        paymentReversal.createdAt = LocalDateTime.now();
        LedgerEntry fineReversal = new LedgerEntry();
        fineReversal.kind = LedgerEntry.Kind.PAYMENT;
        fineReversal.amount = new BigDecimal("20.00");
        fineReversal.description = "REVERSAL:FINE:1:entered in error";
        fineReversal.createdAt = LocalDateTime.now();
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(payment, paymentReversal, fineReversal));
        when(students.findAll()).thenReturn(List.of(student));
        when(students.findAllByOrderByStudentNumberAsc()).thenReturn(List.of(student));
        when(students.count()).thenReturn(1L);
        when(funds.findAll()).thenReturn(List.of());
        DepartmentEvent event = new DepartmentEvent();
        event.id = 3L;
        when(events.findByActiveTrue()).thenReturn(List.of(event));
        Attendance present = new Attendance();
        present.event = event;
        present.status = Attendance.Status.PRESENT;
        when(attendance.count()).thenReturn(1L);
        when(attendance.findAll()).thenReturn(List.of(present));

        var result = api.dashboard();

        assertEquals(new BigDecimal("35.00"), result.get("collections"));
        assertEquals(BigDecimal.ZERO, result.get("unpaid"));
        assertEquals(BigDecimal.ZERO, result.get("fundBalance"));
        assertEquals(100.0, result.get("attendanceRate"));
        assertEquals(1L, result.get("attendanceScans"));
    }

    private FundEntry fund(FundEntry.Type type, String amount, String description) {
        FundEntry entry = new FundEntry();
        entry.type = type;
        entry.amount = new BigDecimal(amount);
        entry.description = description;
        return entry;
    }

    private FundEntry addFundEntry(FundEntry entry, UsernamePasswordAuthenticationToken auth) {
        return api.addFund(new Api.FundInput(entry.type, entry.amount, entry.description), auth);
    }

    private LedgerEntry entry(Long id, LedgerEntry.Kind kind, String amount, String description) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = id;
        entry.kind = kind;
        entry.amount = new BigDecimal(amount);
        entry.description = description;
        entry.createdAt = LocalDateTime.now();
        return entry;
    }
}
