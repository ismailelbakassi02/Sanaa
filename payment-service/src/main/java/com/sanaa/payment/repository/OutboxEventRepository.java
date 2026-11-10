package com.sanaa.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.payment.domain.OutboxEvent;

/**
 * The outbox is intentionally not tenant-scoped: the poller reads across tenants and uses native
 * SQL. Application reads here are only for tests and troubleshooting.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByAggregateIdOrderByIdAsc(Long aggregateId);
}
