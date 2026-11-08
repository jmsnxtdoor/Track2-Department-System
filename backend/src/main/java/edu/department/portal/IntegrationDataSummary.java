package edu.department.portal;

import java.time.Instant;

public record IntegrationDataSummary(
        String dataClassification,
        long studentCount,
        AttendanceSummary attendance,
        Instant generatedAt) {

    public record AttendanceSummary(
            long total,
            long present,
            long late,
            long absent,
            long excused) {}
}
