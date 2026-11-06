package com.sanaa.common.event;

import java.time.Instant;

/**
 * Common Kafka envelope. The tenant id comes from this envelope in consumers, never from a header.
 */
public record EventEnvelope(
        String eventId,
        EventType eventType,
        Instant occurredAt,
        String tenantId,
        PaymentEventPayload payload
) {
}
