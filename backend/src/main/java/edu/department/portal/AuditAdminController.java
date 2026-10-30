package edu.department.portal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditAdminController {
    private final AuditService audit;

    AuditAdminController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditLog> recent() {
        return audit.recent();
    }
}
