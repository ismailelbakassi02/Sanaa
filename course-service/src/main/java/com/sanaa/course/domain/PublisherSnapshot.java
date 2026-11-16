package com.sanaa.course.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cross-tenant publisher reference data, written by the {@code sanaa.publishers.v1} consumer (and
 * by the placeholder rule when a studio creates its first course before the event arrives).
 *
 * <p>Deliberately not {@code @TenantId}: the public catalogue joins this row for every publisher
 * in one query. {@code sourceOccurredAt} is the event time, not the receive time, so an older
 * redelivered event can never overwrite a newer snapshot.
 */
@Entity
@Table(name = "publisher_snapshots")
public class PublisherSnapshot {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "source_occurred_at", nullable = false)
    private Instant sourceOccurredAt;

    protected PublisherSnapshot() {
    }

    public PublisherSnapshot(String tenantId, String slug, String name, String type, String status,
                             Instant sourceOccurredAt) {
        this.tenantId = tenantId;
        this.slug = slug;
        this.name = name;
        this.type = type;
        this.status = status;
        this.sourceOccurredAt = sourceOccurredAt;
    }

    public void update(String slug, String name, String type, String status,
                       Instant sourceOccurredAt) {
        this.slug = slug;
        this.name = name;
        this.type = type;
        this.status = status;
        this.sourceOccurredAt = sourceOccurredAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSourceOccurredAt() {
        return sourceOccurredAt;
    }
}
