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
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 26L;
            return saved;
        });
        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a");

        LedgerEntry reversal = api.reverseLedger(25L, new Api.ReasonInput("Entered in error"), auth);

        assertEquals(LedgerEntry.Kind.PAYMENT, reversal.kind);
        assertEquals(original.amount, reversal.amount);
        assertTrue(reversal.description.startsWith("REVERSAL:FINE:25:"));
        assertEquals("Original fine", original.description);
        assertThrows(ResponseStatusException.class,
                () -> api.reverseLedger(25L, new Api.ReasonInput("Duplicate"), auth));
        verify(students, times(2)).findByIdForUpdate(10L);
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
        assertEquals(100.0, result.get("attendanceRate"));
        assertEquals(1L, result.get("attendanceScans"));
    }
}
