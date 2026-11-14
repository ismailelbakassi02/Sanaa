package com.sanaa.auth.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * Outbox metrics.
 *
 * <p>No tenant id is ever a tag: the outbox is platform-wide, and tenant tags would leak publisher
 * identity into the metrics backend. The {@code application=auth-service} tag comes from
 * {@code management.metrics.tags.application}.
 */
@Component
public class OutboxMetrics {

    private final Counter published;
    private final Counter failures;

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository outboxEventRepository) {
        this.published = Counter.builder("sanaa.outbox.published")
                .description("Outbox rows published to Kafka")
                .register(registry);
        this.failures = Counter.builder("sanaa.outbox.publish.failures")
                .description("Outbox publication attempts that stopped a batch")
                .register(registry);
        // The gauge is evaluated on scrape, which is exactly when the backlog size matters.
        Gauge.builder("sanaa.outbox.unpublished", outboxEventRepository,
                        OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox rows waiting to be published")
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void publishFailed() {
        failures.increment();
    }
}
