package edu.department.portal;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_occurred_at", columnList = "occurred_at DESC"),
        @Index(name = "idx_audit_log_actor_username", columnList = "actor_username")
})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "actor_username", nullable = false, length = 100)
    public String actorUsername;

    @Column(nullable = false, length = 80)
    public String action;

    @Column(name = "entity_type", nullable = false, length = 80)
    public String entityType;

    @Column(name = "entity_id", length = 100)
    public String entityId;

    @Column(name = "occurred_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    public Instant occurredAt = Instant.now();

    @Column(columnDefinition = "text")
    public String details;

    public AuditLog() {}
}
