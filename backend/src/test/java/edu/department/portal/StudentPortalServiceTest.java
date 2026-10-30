package edu.department.portal;

import edu.department.portal.Attendance.Status;
import edu.department.portal.LedgerEntry.Kind;
import edu.department.portal.UserAccount.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentPortalServiceTest {
    private final UserRepo users = mock(UserRepo.class);
    private final LedgerRepo ledger = mock(LedgerRepo.class);
    private final AttendanceRepo attendance = mock(AttendanceRepo.class);
    private final StudentPortalService service = new StudentPortalService(users, ledger, attendance);

    @Test
    void summaryAndPersonalRecordsUseOnlyTheAuthenticatedAccountLink() {
        UserAccount account = linkedAccount("student-one", 21L, "S-021", "Student One");
        when(users.findByUsername("student-one")).thenReturn(Optional.of(account));
        LedgerEntry fine = entry(1L, Kind.FINE, "50.00", "Library fine", "F-1");
        LedgerEntry payment = entry(2L, Kind.PAYMENT, "20.00", "Cash payment", "P-1");
        when(ledger.findByStudentIdOrderByCreatedAtDesc(21L)).thenReturn(List.of(payment, fine));
        Attendance record = new Attendance();
        record.student = account.student;
        record.event = new DepartmentEvent();
        record.event.title = "Orientation";
        record.event.eventDate = LocalDate.of(2026, 10, 1);
        record.scannedAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        record.status = Status.PRESENT;
        when(attendance.findByStudentIdOrderByScannedAtDesc(21L)).thenReturn(List.of(record));

        StudentPortalService.StudentSummary summary = service.summary("student-one");
        List<StudentPortalService.StudentLedgerEntry> entries = service.ledger("student-one");
        List<StudentPortalService.StudentAttendance> attendanceRows = service.attendance("student-one");

        assertEquals("S-021", summary.student().studentNumber());
        assertEquals("Student One", summary.student().fullName());
        assertEquals(new BigDecimal("30.00"), summary.unpaidBalance());
        assertEquals(1, summary.attendance().present());
        assertEquals(List.of("PAYMENT", "FINE"), entries.stream().map(
                StudentPortalService.StudentLedgerEntry::kind).toList());
        assertEquals("Orientation", attendanceRows.get(0).activityName());
        assertEquals("PRESENT", attendanceRows.get(0).status());
        verify(ledger, times(2)).findByStudentIdOrderByCreatedAtDesc(21L);
    }

    @Test
    void unlinkedStudentAccountCannotReadPersonalRecords() {
        UserAccount account = new UserAccount();
        account.username = "student-one";
        account.role = UserRole.STUDENT;
        when(users.findByUsername("student-one")).thenReturn(Optional.of(account));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.summary("student-one"));

        assertEquals(409, failure.getStatusCode().value());
        verifyNoInteractions(ledger, attendance);
    }

    @Test
    void studentRoleIsRequiredEvenIfAccountHasStudentLink() {
        UserAccount account = linkedAccount("staff", 21L, "S-021", "Student One");
        account.role = UserRole.OFFICER;
        when(users.findByUsername("staff")).thenReturn(Optional.of(account));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.summary("staff"));

        assertEquals(403, failure.getStatusCode().value());
        verifyNoInteractions(ledger, attendance);
    }

    private UserAccount linkedAccount(String username, long studentId, String studentNumber, String fullName) {
        UserAccount account = new UserAccount();
        account.username = username;
        account.role = UserRole.STUDENT;
        account.student = new Student();
        account.student.id = studentId;
        account.student.studentNumber = studentNumber;
        account.student.fullName = fullName;
        account.student.course = "BSCS";
        account.student.yearLevel = "2";
        account.student.section = "A";
        return account;
    }

    private LedgerEntry entry(Long id, Kind kind, String amount, String description, String reference) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = id;
        entry.kind = kind;
        entry.amount = new BigDecimal(amount);
        entry.description = description;
        entry.referenceNumber = reference;
        entry.createdAt = LocalDateTime.of(2026, 10, id.intValue(), 10, 0);
        return entry;
    }
}
