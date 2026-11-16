package com.sanaa.course.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cross-tenant instructor name snapshot, keyed by the global user id. Written by the publisher
 * consumer and by the studio placeholder rule; never filtered by {@code @TenantId} because the
 * public course page shows an instructor from any publisher.
 */
@Entity
@Table(name = "instructor_snapshots")
public class InstructorSnapshot {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "source_occurred_at", nullable = false)
    private Instant sourceOccurredAt;

    protected InstructorSnapshot() {
    }

    public InstructorSnapshot(Long userId, String tenantId, String name,
                              Instant sourceOccurredAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.name = name;
        this.sourceOccurredAt = sourceOccurredAt;
    }

    public void update(String tenantId, String name, Instant sourceOccurredAt) {
        this.tenantId = tenantId;
        this.name = name;
        this.sourceOccurredAt = sourceOccurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public Instant getSourceOccurredAt() {
        return sourceOccurredAt;
    }
}
