package edu.department.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        when(attendance.saveAndFlush(any(Attendance.class))).thenAnswer(invocation -> {
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
        verify(attendance, times(1)).saveAndFlush(any(Attendance.class));
        verify(audit).record("officer", "ATTENDANCE_RECORDED", "Attendance", "3",
                "eventId=2;studentId=1;status=LATE");
    }

    @Test
    void createsActivityAsNewEvenWhenRequestContainsAnIdAndCanRetrieveIt() {
        event.id = 99L;
        event.title = "Department Assembly";
        event.eventDate = LocalDate.of(2026, 10, 26);
        when(events.save(any(DepartmentEvent.class))).thenAnswer(invocation -> {
            DepartmentEvent saved = invocation.getArgument(0);
            assertNull(saved.id);
            saved.id = 4L;
            return saved;
        });
        when(events.findAllByOrderByEventDateDesc()).thenReturn(List.of(event));
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        DepartmentEvent created = api.newEvent(event, auth);

        assertEquals(4L, created.id);
        assertSame(event, api.events().get(0));
        verify(audit).record("officer", "ATTENDANCE_EVENT_CREATED", "DepartmentEvent", "4",
                "title=Department Assembly");
    }

    @Test
    void attendanceWithoutExplicitStatusDefaultsToPresent() {
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");
        LocalDateTime startedAt = LocalDateTime.now();

        Attendance saved = api.scan(new Api.ScanInput(2L, " S-1 ", null), auth);

        assertEquals(Attendance.Status.PRESENT, saved.status);
        assertNotNull(saved.scannedAt);
        assertFalse(saved.scannedAt.isBefore(startedAt.minusSeconds(1)));
        assertFalse(saved.scannedAt.isAfter(LocalDateTime.now().plusSeconds(1)));
        verify(students).findByStudentNumber("S-1");
    }

    @Test
    void allowsScanDateDifferentFromActivityDate() {
        LocalDate eventDate = LocalDate.now().minusDays(5);
        event.eventDate = eventDate;
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        Attendance saved = api.scan(
                new Api.ScanInput(2L, "S-1", Attendance.Status.PRESENT), auth);

        assertEquals(eventDate, saved.event.eventDate);
        assertTrue(saved.scannedAt.toLocalDate().isAfter(saved.event.eventDate));
    }

    @Test
    void unknownActivityAndStudentReturnNotFoundWithoutSaving() {
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");
        when(events.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException missingEvent = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(404L, "S-1", Attendance.Status.PRESENT), auth));
        assertEquals(HttpStatus.NOT_FOUND, missingEvent.getStatusCode());

        when(students.findByStudentNumber("INVALID")).thenReturn(Optional.empty());
        ResponseStatusException missingStudent = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(2L, "INVALID", Attendance.Status.PRESENT), auth));
        assertEquals(HttpStatus.NOT_FOUND, missingStudent.getStatusCode());
        verify(attendance, never()).saveAndFlush(any(Attendance.class));
    }

    @Test
    void inactiveActivityRejectsAttendance() {
        event.active = false;
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(2L, "S-1", Attendance.Status.PRESENT), auth));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(students, never()).findByStudentNumber(any());
        verify(attendance, never()).saveAndFlush(any(Attendance.class));
    }

    @Test
    void databaseUniquenessRaceIsReportedAsDuplicateSubmission() {
        when(attendance.existsByStudentIdAndEventId(1L, 2L)).thenReturn(false);
        when(attendance.saveAndFlush(any(Attendance.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate student/event"));
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(2L, "S-1", Attendance.Status.PRESENT), auth));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void statusUpdatePersistsNewStatusAndRecordsAudit() {
        Attendance record = new Attendance();
        record.id = 8L;
        record.status = Attendance.Status.PRESENT;
        when(attendance.findById(8L)).thenReturn(Optional.of(record));
        when(attendance.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        Attendance updated = api.updateAttendance(8L,
                new Api.AttendanceStatusInput(Attendance.Status.EXCUSED), auth);

        assertEquals(Attendance.Status.EXCUSED, updated.status);
        verify(attendance).save(record);
        verify(audit).record("officer", "ATTENDANCE_STATUS_UPDATED", "Attendance", "8",
                "status=EXCUSED");
    }

    @Test
    void statusUpdateForMissingRecordReturnsNotFound() {
        when(attendance.findById(404L)).thenReturn(Optional.empty());
        var auth = new UsernamePasswordAuthenticationToken("officer", "n/a");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> api.updateAttendance(404L,
                        new Api.AttendanceStatusInput(Attendance.Status.LATE), auth));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(attendance, never()).save(any(Attendance.class));
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

    @Test
    void attendanceReportCountsStatusesForActivityWithoutRequiringSameScanDate() {
        event.title = "Historical makeup class";
        event.eventDate = LocalDate.of(2026, 1, 10);
        Attendance present = attendanceFor(Attendance.Status.PRESENT,
                LocalDateTime.of(2026, 2, 3, 10, 0));
        Attendance late = attendanceFor(Attendance.Status.LATE,
                LocalDateTime.of(2026, 2, 3, 10, 5));
        Attendance absent = attendanceFor(Attendance.Status.ABSENT,
                LocalDateTime.of(2026, 2, 3, 10, 10));
        Attendance excused = attendanceFor(Attendance.Status.EXCUSED,
                LocalDateTime.of(2026, 2, 3, 10, 15));
        when(events.findAllByOrderByEventDateDesc()).thenReturn(List.of(event));
        when(attendance.findByEventId(2L)).thenReturn(List.of(present, late, absent, excused));

        var row = api.attendanceReport().get(0);

        assertEquals(4, row.get("total"));
        assertEquals(1L, row.get("present"));
        assertEquals(1L, row.get("late"));
        assertEquals(1L, row.get("absent"));
        assertEquals(1L, row.get("excused"));
        assertEquals(LocalDate.of(2026, 1, 10), row.get("eventDate"));
    }

    private Attendance attendanceFor(Attendance.Status status, LocalDateTime scannedAt) {
        Attendance record = new Attendance();
        record.event = event;
        record.student = student;
        record.status = status;
        record.scannedAt = scannedAt;
        return record;
    }
}
