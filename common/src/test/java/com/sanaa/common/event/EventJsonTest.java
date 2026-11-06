package com.sanaa.common.event;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventJsonTest {

    @Test
    void roundTripsEnvelopeAndKeepsIsoInstant() {
        PaymentEventPayload payload = new PaymentEventPayload(
                1L, 5L, "student@example.com", "Student", 9L, "Course", "299.00", "MAD", null);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                EventType.PAYMENT_SUBMITTED,
                Instant.parse("2026-01-01T12:00:00Z"),
                "11111111-1111-1111-1111-111111111111",
                payload);

        String json = EventJson.toJson(envelope);

        assertThat(json).contains("\"eventType\":\"PAYMENT_SUBMITTED\"");
        assertThat(json).contains("\"occurredAt\":\"2026-01-01T12:00:00Z\"");
        assertThat(json).doesNotContain("\"reason\"");

        EventEnvelope decoded = EventJson.fromJson(json, EventEnvelope.class);

        assertThat(decoded).isEqualTo(envelope);
    }

    @Test
    void ignoresUnknownProperties() {
        String json = """
                {
                  "eventId": "evt-1",
                  "eventType": "PAYMENT_APPROVED",
                  "occurredAt": "2026-01-01T12:00:00Z",
                  "tenantId": "11111111-1111-1111-1111-111111111111",
                  "futureField": "ignored",
                  "payload": {
                    "paymentId": 1,
                    "userId": 5,
                    "userEmail": "student@example.com",
                    "userName": "Student",
                    "courseId": 9,
                    "courseTitle": "Course",
                    "amount": "299.00",
                    "currency": "MAD"
                  }
                }
                """;

        EventEnvelope decoded = EventJson.fromJson(json, EventEnvelope.class);

        assertThat(decoded.eventType()).isEqualTo(EventType.PAYMENT_APPROVED);
        assertThat(decoded.payload().reason()).isNull();
    }
}
