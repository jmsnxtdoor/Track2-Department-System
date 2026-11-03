package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "track2.test.jdbcUrl", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
        "spring.datasource.url=${track2.test.jdbcUrl}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${track2.test.username}",
        "spring.datasource.******",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({Api.class, AuditService.class, StudentPortalService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportsDashboardPostgresIntegrationTest {
    private static final UsernamePasswordAuthenticationToken ADMIN =
            new UsernamePasswordAuthenticationToken("admin", "n/a");
    private static final UsernamePasswordAuthenticationToken OFFICER =
            new UsernamePasswordAuthenticationToken("officer", "n/a");
    private static final UsernamePasswordAuthenticationToken TREASURER =
            new UsernamePasswordAuthenticationToken("treasurer", "n/a");

    @Autowired private Api api;
    @Autowired private StudentRepo students;
    @Autowired private EventRepo events;
    @Autowired private LedgerRepo ledger;
    @Autowired private FundRepo funds;
    @Autowired private AttendanceRepo attendance;

    @Test
    void reportsAndDashboardMatchPostgresAcrossPaymentsAttendanceAndFundCorrections() {
        Student newStudent = new Student();
        newStudent.studentNumber = "RPT-" + UUID.randomUUID();
        newStudent.fullName = "Fictional Report Student";
        Student student = students.saveAndFlush(newStudent);

        LedgerEntry fine = api.addLedger(new Api.LedgerInput(student.id, LedgerEntry.Kind.FINE,
                new BigDecimal("100.00"), "Report fine", "RPT-FINE-" + UUID.randomUUID()), TREASURER);
        assertTrue(fine.id != null);
        LedgerEntry payment = api.addLedger(new Api.LedgerInput(student.id, LedgerEntry.Kind.PAYMENT,
                new BigDecimal("40.00"), "Report partial payment", "RPT-PAY-" + UUID.randomUUID()), TREASURER);

        FundEntry income = fund(FundEntry.Type.OPENING_BALANCE, "1000.00");
        api.addFund(income, TREASURER);
        api.addFund(fund(FundEntry.Type.DISBURSEMENT, "250.00"), TREASURER);
        FundEntry expense = api.addFund(fund(FundEntry.Type.DISBURSEMENT, "150.00"), TREASURER);

        DepartmentEvent newEvent = new DepartmentEvent();
        newEvent.title = "Fictional report event";
        newEvent.eventDate = LocalDate.now();
        DepartmentEvent event = events.saveAndFlush(newEvent);
        Attendance record = api.scan(new Api.ScanInput(event.id, student.studentNumber,
                Attendance.Status.PRESENT), OFFICER);

        var dashboard = api.dashboard();
        assertEquals(1L, dashboard.get("students"));
        assertEquals(new BigDecimal("40.00"), dashboard.get("collections"));
        assertEquals(new BigDecimal("60.00"), dashboard.get("unpaid"));
        assertEquals(new BigDecimal("600.00"), dashboard.get("fundBalance"));
        assertEquals(1L, dashboard.get("attendanceScans"));
        assertEquals(100.0, dashboard.get("attendanceRate"));

        var finance = api.financeReport();
        assertEquals(new BigDecimal("40.00"), finance.get("totalCollections"));
        assertEquals(2, ledger.findByStudentIdOrderByCreatedAtDesc(student.id).size());
        var balances = (java.util.List<?>) finance.get("balances");
        assertEquals(1, balances.size());
        assertEquals(new BigDecimal("60.00"),
                ((java.util.Map<?, ?>) balances.get(0)).get("unpaidBalance"));
        assertEquals(3, api.fundsReport().size());

        var attendanceSummary = api.attendanceReport().get(0);
        assertEquals(1, attendanceSummary.get("total"));
        assertEquals(1L, attendanceSummary.get("present"));
        assertEquals(0L, attendanceSummary.get("late"));
        assertEquals(record.id, attendance.findByEventId(event.id).get(0).id);
        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(event.id, student.studentNumber, Attendance.Status.PRESENT), OFFICER));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals(1, attendance.findByEventId(event.id).size());

        api.reverseLedger(payment.id, new Api.ReasonInput("Report correction"), ADMIN);
        api.reverseFund(expense.id, new Api.ReasonInput("Expense correction"), ADMIN);
        var correctedDashboard = api.dashboard();
        assertEquals(BigDecimal.ZERO.setScale(2), correctedDashboard.get("collections"));
        assertEquals(new BigDecimal("100.00"), correctedDashboard.get("unpaid"));
        assertEquals(new BigDecimal("750.00"), correctedDashboard.get("fundBalance"));
        assertEquals(BigDecimal.ZERO.setScale(2), api.financeReport().get("totalCollections"));
        assertTrue(funds.findAll().stream().anyMatch(row -> row.description.startsWith(
                "REVERSAL:DISBURSEMENT:" + expense.id + ":")));
    }

    private FundEntry fund(FundEntry.Type type, String amount) {
        FundEntry entry = new FundEntry();
        entry.type = type;
        entry.amount = new BigDecimal(amount);
        entry.description = "Fictional report " + UUID.randomUUID();
        return entry;
    }
}
