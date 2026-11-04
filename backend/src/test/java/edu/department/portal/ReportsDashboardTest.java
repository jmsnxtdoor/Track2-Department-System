package edu.department.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportsDashboardTest {
    private StudentRepo students;
    private LedgerRepo ledger;
    private FundRepo funds;
    private EventRepo events;
    private AttendanceRepo attendance;
    private Api api;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepo.class);
        ledger = mock(LedgerRepo.class);
        funds = mock(FundRepo.class);
        events = mock(EventRepo.class);
        attendance = mock(AttendanceRepo.class);
        api = new Api(students, ledger, funds, events, attendance, mock(AuditService.class),
                mock(StudentPortalService.class));
    }

    @Test
    void financialReportsReconcileWithDashboardIncludingPaymentReversal() {
        Student first = student(1L, "S-001");
        Student second = student(2L, "S-002");
        LedgerEntry fine = ledgerEntry(first, LedgerEntry.Kind.FINE, "100.00", "Fine");
        LedgerEntry payment = ledgerEntry(first, LedgerEntry.Kind.PAYMENT, "40.00", "Payment");
        LedgerEntry reversal = ledgerEntry(first, LedgerEntry.Kind.FINE, "10.00",
                "REVERSAL:PAYMENT:2:Correction");
        LedgerEntry settledFine = ledgerEntry(second, LedgerEntry.Kind.FINE, "25.00", "Settled fine");
        LedgerEntry settledPayment = ledgerEntry(second, LedgerEntry.Kind.PAYMENT, "25.00", "Settled payment");
        List<LedgerEntry> entries = List.of(payment, reversal, fine, settledPayment, settledFine);

        FundEntry income = fundEntry(FundEntry.Type.OPENING_BALANCE, "1000.00");
        FundEntry expense = fundEntry(FundEntry.Type.DISBURSEMENT, "250.00");
        List<FundEntry> fundEntries = List.of(expense, income);

        when(students.count()).thenReturn(2L);
        when(students.findAll()).thenReturn(List.of(first, second));
        when(students.findAllByOrderByStudentNumberAsc()).thenReturn(List.of(first, second));
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(entries);
        when(ledger.findByStudentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(payment, reversal, fine));
        when(ledger.findByStudentIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(settledPayment, settledFine));
        when(funds.findAll()).thenReturn(fundEntries);
        when(funds.findAllByOrderByCreatedAtDesc()).thenReturn(fundEntries);
        when(events.findByActiveTrue()).thenReturn(List.of());
        when(attendance.count()).thenReturn(0L);
        when(attendance.findAll()).thenReturn(List.of());

        var dashboard = api.dashboard();
        var report = api.financeReport();
        List<?> balances = (List<?>) report.get("balances");

        assertEquals(new BigDecimal("55.00"), dashboard.get("collections"));
        assertEquals(dashboard.get("collections"), report.get("totalCollections"));
        assertEquals(new BigDecimal("70.00"), dashboard.get("unpaid"));
        assertEquals(new BigDecimal("750.00"), dashboard.get("fundBalance"));
        assertEquals(new BigDecimal("70.00"), ((Map<?, ?>) balances.get(0)).get("unpaidBalance"));
        assertEquals(BigDecimal.ZERO.setScale(2), ((Map<?, ?>) balances.get(1)).get("unpaidBalance"));
        assertEquals(2, api.fundsReport().size());
    }

    @Test
    void attendanceReportCountsEachStatusAndDashboardUsesActiveEvents() {
        Student first = student(1L, "S-001");
        Student second = student(2L, "S-002");
        DepartmentEvent active = event(10L, "Assembly", true);
        DepartmentEvent inactive = event(11L, "Past activity", false);
        List<Attendance> activeRecords = List.of(
                record(first, active, Attendance.Status.PRESENT),
                record(second, active, Attendance.Status.LATE),
                record(student(3L, "S-003"), active, Attendance.Status.ABSENT));
        List<Attendance> inactiveRecords = List.of(
                record(first, inactive, Attendance.Status.EXCUSED));

        when(students.count()).thenReturn(2L);
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(funds.findAll()).thenReturn(List.of());
        when(events.findByActiveTrue()).thenReturn(List.of(active));
        when(events.findAllByOrderByEventDateDesc()).thenReturn(List.of(active, inactive));
        when(attendance.count()).thenReturn(4L);
        when(attendance.findAll()).thenReturn(List.of(
                activeRecords.get(0), activeRecords.get(1), activeRecords.get(2), inactiveRecords.get(0)));
        when(attendance.findByEventId(10L)).thenReturn(activeRecords);
        when(attendance.findByEventId(11L)).thenReturn(inactiveRecords);

        var dashboard = api.dashboard();
        List<Map<String, Object>> report = api.attendanceReport();

        assertEquals(4L, dashboard.get("attendanceScans"));
        assertEquals(100.0, dashboard.get("attendanceRate"));
        assertEquals(3, report.get(0).get("total"));
        assertEquals(1L, report.get(0).get("present"));
        assertEquals(1L, report.get(0).get("late"));
        assertEquals(1L, report.get(0).get("absent"));
        assertEquals(0L, report.get(0).get("excused"));
        assertEquals(1, report.get(1).get("total"));
        assertEquals(1L, report.get(1).get("excused"));
    }

    @Test
    void emptyReportsAndDashboardReturnZeroTotals() {
        when(students.count()).thenReturn(0L);
        when(students.findAll()).thenReturn(List.of());
        when(students.findAllByOrderByStudentNumberAsc()).thenReturn(List.of());
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(funds.findAll()).thenReturn(List.of());
        when(funds.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(events.findByActiveTrue()).thenReturn(List.of());
        when(events.findAllByOrderByEventDateDesc()).thenReturn(List.of());
        when(attendance.count()).thenReturn(0L);
        when(attendance.findAll()).thenReturn(List.of());

        var dashboard = api.dashboard();

        assertEquals(0L, dashboard.get("students"));
        assertEquals(BigDecimal.ZERO, dashboard.get("collections"));
        assertEquals(BigDecimal.ZERO, dashboard.get("unpaid"));
        assertEquals(BigDecimal.ZERO, dashboard.get("fundBalance"));
        assertEquals(0.0, dashboard.get("attendanceRate"));
        assertEquals(List.of(), ((Map<?, ?>) api.financeReport()).get("balances"));
        assertTrue(api.fundsReport().isEmpty());
        assertTrue(api.attendanceReport().isEmpty());
    }

    @Test
    void dashboardOnlyIncludesMetricsForTheAuthenticatedRole() {
        when(students.count()).thenReturn(0L);
        when(students.findAll()).thenReturn(List.of());
        when(ledger.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(funds.findAll()).thenReturn(List.of());
        when(events.findByActiveTrue()).thenReturn(List.of());
        when(attendance.count()).thenReturn(0L);
        when(attendance.findAll()).thenReturn(List.of());

        var officerDashboard = api.dashboard(authentication("OFFICER"));

        assertTrue(officerDashboard.containsKey("attendanceRate"));
        assertFalse(officerDashboard.containsKey("collections"));
        assertFalse(officerDashboard.containsKey("fundBalance"));
        verifyNoInteractions(ledger, funds);

        clearInvocations(students, ledger, funds, events, attendance);
        var treasurerDashboard = api.dashboard(authentication("TREASURER"));
        assertTrue(treasurerDashboard.containsKey("collections"));
        assertFalse(treasurerDashboard.containsKey("attendanceRate"));
        verifyNoInteractions(events, attendance);
    }

    private static UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken("test",
                "n/a", Set.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Student student(Long id, String number) {
        Student student = new Student();
        student.id = id;
        student.studentNumber = number;
        student.fullName = "Test " + number;
        return student;
    }

    private static LedgerEntry ledgerEntry(Student student, LedgerEntry.Kind kind, String amount, String description) {
        LedgerEntry entry = new LedgerEntry();
        entry.student = student;
        entry.kind = kind;
        entry.amount = new BigDecimal(amount);
        entry.description = description;
        return entry;
    }

    private static FundEntry fundEntry(FundEntry.Type type, String amount) {
        FundEntry entry = new FundEntry();
        entry.type = type;
        entry.amount = new BigDecimal(amount);
        entry.description = "Test fund entry";
        return entry;
    }

    private static DepartmentEvent event(Long id, String title, boolean active) {
        DepartmentEvent event = new DepartmentEvent();
        event.id = id;
        event.title = title;
        event.eventDate = LocalDate.now();
        event.active = active;
        return event;
    }

    private static Attendance record(Student student, DepartmentEvent event, Attendance.Status status) {
        Attendance record = new Attendance();
        record.student = student;
        record.event = event;
        record.status = status;
        return record;
    }
}
