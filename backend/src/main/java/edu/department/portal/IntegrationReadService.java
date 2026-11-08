package edu.department.portal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class IntegrationReadService {
    private final StudentRepo students;
    private final AttendanceRepo attendance;

    IntegrationReadService(StudentRepo students, AttendanceRepo attendance) {
        this.students = students;
        this.attendance = attendance;
    }

    @Transactional(readOnly = true)
    IntegrationDataSummary dataSummary() {
        IntegrationDataSummary.AttendanceSummary attendanceSummary = new IntegrationDataSummary.AttendanceSummary(
                attendance.count(),
                attendance.countByStatus(Attendance.Status.PRESENT),
                attendance.countByStatus(Attendance.Status.LATE),
                attendance.countByStatus(Attendance.Status.ABSENT),
                attendance.countByStatus(Attendance.Status.EXCUSED));
        return new IntegrationDataSummary(
                "NON_PERSONAL_AGGREGATE",
                students.count(),
                attendanceSummary,
                Instant.now());
    }
}
