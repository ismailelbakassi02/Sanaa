package com.sanaa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sanaa.common.event.EventType;

/**
 * Transactional outbox row written in the same transaction as the publisher change.
 *
 * <p>WHY no {@code @TenantId}: the poller selects unpublished rows across all publishers with one
 * native query, because publishing is a platform-wide concern. The tenant id is stored as data and
 * copied into the event envelope, not used as a Hibernate filter. Same design as payment-service
 * (ADR 008).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 36)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private EventType eventType;

    @Column(name = "topic", nullable = false, updatable = false, length = 64)
    private String topic;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventId, String tenantId, String aggregateId, EventType eventType,
                       String topic, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
