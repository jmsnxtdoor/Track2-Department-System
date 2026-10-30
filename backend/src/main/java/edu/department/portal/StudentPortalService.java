package edu.department.portal;

import edu.department.portal.Attendance.Status;
import edu.department.portal.UserAccount.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentPortalService {
    private static final String REVERSAL_PREFIX = "REVERSAL:";

    private final UserRepo users;
    private final LedgerRepo ledger;
    private final AttendanceRepo attendance;

    StudentPortalService(UserRepo users, LedgerRepo ledger, AttendanceRepo attendance) {
        this.users = users;
        this.ledger = ledger;
        this.attendance = attendance;
    }

    @Transactional(readOnly = true)
    public StudentSummary summary(String username) {
        Student student = linkedStudent(username);
        List<LedgerEntry> entries = ledger.findByStudentIdOrderByCreatedAtDesc(student.id);
        List<Attendance> records = attendance.findByStudentIdOrderByScannedAtDesc(student.id);
        BigDecimal balance = entries.stream()
                .map(entry -> entry.kind == LedgerEntry.Kind.FINE ? entry.amount : entry.amount.negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO);
        AttendanceSummary attendanceSummary = new AttendanceSummary(
                records.size(),
                count(records, Status.PRESENT),
                count(records, Status.LATE),
                count(records, Status.ABSENT),
                count(records, Status.EXCUSED));
        return new StudentSummary(
                username,
                StudentProfile.from(student),
                balance,
                attendanceSummary);
    }

    @Transactional(readOnly = true)
    public List<StudentLedgerEntry> ledger(String username) {
        Student student = linkedStudent(username);
        List<LedgerEntry> entries = ledger.findByStudentIdOrderByCreatedAtDesc(student.id);
        return entries.stream()
                .map(entry -> new StudentLedgerEntry(
                        entry.id,
                        entry.kind.name(),
                        entry.amount,
                        entry.description,
                        entry.referenceNumber,
                        entry.createdAt,
                        entry.description.startsWith(REVERSAL_PREFIX),
                        !entry.description.startsWith(REVERSAL_PREFIX)
                                && ledger.existsByDescriptionStartingWith(
                                        reversalMarker(entry.kind.name(), entry.id))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentAttendance> attendance(String username) {
        Student student = linkedStudent(username);
        return attendance.findByStudentIdOrderByScannedAtDesc(student.id).stream()
                .map(record -> new StudentAttendance(
                        record.event.title,
                        record.event.eventDate,
                        record.scannedAt,
                        record.status.name()))
                .toList();
    }

    private Student linkedStudent(String username) {
        UserAccount account = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student account not found."));
        if (account.role != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not a student account.");
        }
        if (account.student == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your student account is not linked to a student record. Contact the department administrator.");
        }
        return account.student;
    }

    private long count(List<Attendance> records, Status status) {
        return records.stream().filter(record -> record.status == status).count();
    }

    private String reversalMarker(String type, Long id) {
        return REVERSAL_PREFIX + type + ":" + id + ":";
    }

    public record StudentSummary(
            String username,
            StudentProfile student,
            BigDecimal unpaidBalance,
            AttendanceSummary attendance) {}

    public record StudentProfile(
            String studentNumber,
            String fullName,
            String course,
            String yearLevel,
            String section) {
        static StudentProfile from(Student student) {
            return new StudentProfile(
                    student.studentNumber,
                    student.fullName,
                    student.course,
                    student.yearLevel,
                    student.section);
        }
    }

    public record AttendanceSummary(
            long total,
            long present,
            long late,
            long absent,
            long excused) {}

    public record StudentLedgerEntry(
            Long id,
            String kind,
            BigDecimal amount,
            String description,
            String referenceNumber,
            LocalDateTime createdAt,
            boolean correction,
            boolean corrected) {}

    public record StudentAttendance(
            String activityName,
            LocalDate activityDate,
            LocalDateTime scannedAt,
            String status) {}
}
