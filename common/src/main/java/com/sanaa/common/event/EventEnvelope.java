package com.sanaa.common.event;

import java.time.Instant;

/**
 * Common Kafka envelope. The tenant id comes from this envelope in consumers, never from a header.
 *
 * <p>WHY generic: every topic shares the same outer contract but each one has its own payload
 * ({@link PaymentEventPayload}, {@link PublisherEventPayload}, {@link InstructorEventPayload}).
 * Consumers deserialize with the payload class they expect, so the compiler keeps the payload
 * access typed instead of returning a map.
 */
public record EventEnvelope<T>(
        String eventId,
        EventType eventType,
        Instant occurredAt,
        String tenantId,
        T payload
) {
}
