package com.sanaa.course.event;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

/**
 * Idempotency ledger shared by all tenants.
 *
 * <p>WHY native SQL: {@code processed_events} is deliberately not tenant-owned (an event id is
 * unique across the platform), so it has no {@code @TenantId} and cannot use the tenant-scoped
 * repository layer.
 *
 * <p>WHY {@code INSERT IGNORE}: a plain insert would raise a duplicate-key error on redelivery and,
 * inside the surrounding transaction, mark it rollback-only. {@code INSERT IGNORE} instead reports
 * "zero rows inserted", which is the duplicate signal the caller needs without breaking the
 * transaction.
 */
@Component
public class ProcessedEventRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @return 1 when the event id was new, 0 when it had already been processed
     */
    public int markProcessed(String eventId) {
        return entityManager.createNativeQuery("""
                        INSERT IGNORE INTO processed_events (event_id, processed_at)
                        VALUES (:eventId, :processedAt)
                        """)
                .setParameter("eventId", eventId)
                // DATETIME(6) has no zone; write UTC explicitly instead of relying on the JVM zone.
                .setParameter("processedAt", LocalDateTime.now(ZoneOffset.UTC))
                .executeUpdate();
    }
}
