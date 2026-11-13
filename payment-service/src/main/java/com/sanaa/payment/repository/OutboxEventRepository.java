package com.sanaa.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanaa.payment.domain.OutboxEvent;

/**
 * The outbox is intentionally not tenant-scoped: the poller reads across tenants and uses native
 * SQL. Application reads here are only for tests and troubleshooting.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByAggregateIdOrderByIdAsc(Long aggregateId);

    /**
     * Locks the next unpublished batch for this transaction only.
     *
     * <p>Native SQL on a tenant-owned table is forbidden, and this table is the documented
     * exception: publication is a platform concern, so {@code outbox_events} has no
     * {@code @TenantId} and no tenant can be used to filter it.
     *
     * <p>{@code SKIP LOCKED} lets a second instance take the next rows instead of waiting, which
     * keeps several pollers running side by side without ever publishing the same row twice.
     * {@code ORDER BY id} preserves the order of the events of one payment.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
