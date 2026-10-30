package edu.department.portal;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {
    private final AuditRepo auditRepo;

    AuditService(AuditRepo auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional
    public void record(String actor, String action, String entityType, String entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.actorUsername = actor;
        entry.action = action;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.details = details;
        auditRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent() {
        return auditRepo.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 500));
    }
}
