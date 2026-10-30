package edu.department.portal;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    private final AuditRepo auditRepo = mock(AuditRepo.class);
    private final AuditService service = new AuditService(auditRepo);

    @Test
    void recordPersistsActorActionAndAffectedRecord() {
        service.record("officer", "ATTENDANCE_RECORDED", "Attendance", "42", "eventId=5;studentId=9");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepo).save(captor.capture());
        AuditLog entry = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("officer", entry.actorUsername);
        org.junit.jupiter.api.Assertions.assertEquals("ATTENDANCE_RECORDED", entry.action);
        org.junit.jupiter.api.Assertions.assertEquals("Attendance", entry.entityType);
        org.junit.jupiter.api.Assertions.assertEquals("42", entry.entityId);
        org.junit.jupiter.api.Assertions.assertNotNull(entry.occurredAt);
        org.junit.jupiter.api.Assertions.assertEquals("eventId=5;studentId=9", entry.details);
    }
}
