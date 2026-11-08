package edu.department.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "integration_api_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_integration_api_key_hash",
                columnNames = "key_hash"))
class IntegrationApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "key_hash", nullable = false, length = 64)
    String keyHash;

    @Column(name = "created_by", nullable = false, length = 100)
    String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    Instant revokedAt;

    protected IntegrationApiKey() {}
}
