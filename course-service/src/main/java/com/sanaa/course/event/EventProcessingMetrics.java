package com.sanaa.course.event;

import java.util.EnumMap;
import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * {@code sanaa_events_processed_total} by outcome.
 *
 * <p>No tenant tag: tenant ids would multiply the series without adding operational value. The
 * {@code application=course-service} tag comes from {@code management.metrics.tags.application}.
 */
@Component
public class EventProcessingMetrics {

    private final Map<EventProcessingOutcome, Counter> counters =
            new EnumMap<>(EventProcessingOutcome.class);

    public EventProcessingMetrics(MeterRegistry registry) {
        for (EventProcessingOutcome outcome : EventProcessingOutcome.values()) {
            counters.put(outcome, Counter.builder("sanaa.events.processed")
                    .description("Payment events handled by the course-service consumer")
                    .tag("result", outcome.tag())
                    .register(registry));
        }
    }

    public void record(EventProcessingOutcome outcome) {
        counters.get(outcome).increment();
    }
}
