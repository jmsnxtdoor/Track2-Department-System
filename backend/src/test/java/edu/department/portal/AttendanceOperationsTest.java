package edu.department.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttendanceOperationsTest {
    private StudentRepo students;
    private LedgerRepo ledger;
    private FundRepo funds;
    private EventRepo events;
    private AttendanceRepo attendance;
    private AuditService audit;
    private Api api;
    private Student student;
    private DepartmentEvent event;

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
        student.id = 1L;
        student.studentNumber = "S-1";
        event = new DepartmentEvent();
        event.id = 2L;
        event.active = true;
        when(events.findById(2L)).thenReturn(Optional.of(event));
        when(students.findByStudentNumber("S-1")).thenReturn(Optional.of(student));
        when(attendance.existsByStudentIdAndEventId(1L, 2L)).thenReturn(false, true);
        when(attendance.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance record = invocation.getArgument(0);
            record.id = 3L;
            return record;
        });
    }

    @Test
    void recordsSelectedStatusAndRejectsDuplicateStudentEvent() {
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        Attendance saved = api.manualAttendance(
                new Api.ScanInput(2L, "S-1", Attendance.Status.LATE), auth);

        assertEquals(Attendance.Status.LATE, saved.status);
        assertSame(event, saved.event);
        assertSame(student, saved.student);
        assertThrows(ResponseStatusException.class, () -> api.manualAttendance(
                new Api.ScanInput(2L, "S-1", Attendance.Status.PRESENT), auth));
        verify(attendance, times(1)).save(any(Attendance.class));
        verify(audit).record("officer", "ATTENDANCE_RECORDED", "Attendance", "3",
                "eventId=2;studentId=1;status=LATE");
    }

    @Test
    void attendanceReportCountsEverySupportedStatusByEvent() {
        when(events.findAllByOrderByEventDateDesc()).thenReturn(java.util.List.of(event));
        event.title = "Department Assembly";
        event.eventDate = java.time.LocalDate.of(2026, 10, 26);
        Attendance present = new Attendance();
        present.status = Attendance.Status.PRESENT;
        Attendance excused = new Attendance();
        excused.status = Attendance.Status.EXCUSED;
        when(attendance.findByEventId(2L)).thenReturn(java.util.List.of(present, excused));

        var row = api.attendanceReport().get(0);

        assertEquals(2, row.get("total"));
        assertEquals(1L, row.get("present"));
        assertEquals(1L, row.get("excused"));
        assertEquals(0L, row.get("absent"));
    }
}
