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
        EventEnvelope<PaymentEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.PAYMENT_SUBMITTED,
                Instant.parse("2026-01-01T12:00:00Z"),
                "11111111-1111-1111-1111-111111111111",
                payload);

        String json = EventJson.toJson(envelope);

        assertThat(json).contains("\"eventType\":\"PAYMENT_SUBMITTED\"");
        assertThat(json).contains("\"occurredAt\":\"2026-01-01T12:00:00Z\"");
        assertThat(json).doesNotContain("\"reason\"");

        EventEnvelope<PaymentEventPayload> decoded =
                EventJson.fromJson(json, PaymentEventPayload.class);

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

        EventEnvelope<PaymentEventPayload> decoded =
                EventJson.fromJson(json, PaymentEventPayload.class);

        assertThat(decoded.eventType()).isEqualTo(EventType.PAYMENT_APPROVED);
        assertThat(decoded.payload().reason()).isNull();
    }

    @Test
    void roundTripsPublisherPayloadOnThePublisherTopic() {
        PublisherEventPayload payload = new PublisherEventPayload(
                "11111111-1111-1111-1111-111111111111", "atlas", "Atlas Academy", "ORGANIZATION",
                "ACTIVE", "contact@atlas.local", "#195A56");
        EventEnvelope<PublisherEventPayload> envelope = new EventEnvelope<>(
                "evt-publisher", EventType.PUBLISHER_UPSERTED, Instant.parse("2026-01-01T12:00:00Z"),
                "11111111-1111-1111-1111-111111111111", payload);

        String json = EventJson.toJson(envelope);

        assertThat(json).contains("\"eventType\":\"PUBLISHER_UPSERTED\"");
        assertThat(json).contains("\"type\":\"ORGANIZATION\"");

        EventEnvelope<PublisherEventPayload> decoded =
                EventJson.fromJson(json, PublisherEventPayload.class);
        assertThat(decoded.payload()).isEqualTo(payload);
        assertThat(decoded.payload().primaryColor()).isEqualTo("#195A56");
    }

    @Test
    void roundTripsInstructorPayload() {
        InstructorEventPayload payload = new InstructorEventPayload(
                42L, "11111111-1111-1111-1111-111111111111", "Yasmine El Amrani");
        EventEnvelope<InstructorEventPayload> envelope = new EventEnvelope<>(
                "evt-instructor", EventType.INSTRUCTOR_UPDATED, Instant.parse("2026-01-01T12:00:00Z"),
                "11111111-1111-1111-1111-111111111111", payload);

        EventEnvelope<InstructorEventPayload> decoded =
                EventJson.fromJson(EventJson.toJson(envelope), InstructorEventPayload.class);

        assertThat(decoded.payload()).isEqualTo(payload);
        assertThat(decoded.payload().userId()).isEqualTo(42L);
    }
}
