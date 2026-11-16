package com.sanaa.course.event;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.event.InstructorEventPayload;
import com.sanaa.common.event.PublisherEventPayload;
import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;

/**
 * Parses and validates a publisher-topic record before any database work.
 *
 * <p>WHY the type is read from a tree first: the topic carries {@code PUBLISHER_UPSERTED} with a
 * {@link PublisherEventPayload} and {@code INSTRUCTOR_UPDATED} with an
 * {@link InstructorEventPayload}. Deserializing into the wrong record would silently produce an
 * empty payload, so the type selects the record before validation.
 *
 * <p>Every failure here is non-retryable: the same bytes would fail again on every attempt, so the
 * record belongs in {@code sanaa.publishers.v1.DLT}.
 */
@Component
public class PublisherEventValidator {

    private static final UUID NIL_TENANT = UUID.fromString(TenantContext.NIL_TENANT);

    public PublisherEvent validate(String raw) {
        JsonNode tree;
        try {
            tree = EventJson.mapper().readTree(raw);
        } catch (RuntimeException exception) {
            throw new NonRetryableEventException("malformed event JSON", exception);
        }
        if (tree == null || !tree.isObject()) {
            throw new NonRetryableEventException("event envelope is missing");
        }

        String typeText = tree.path("eventType").isTextual()
                ? tree.get("eventType").asString() : null;
        if (typeText == null) {
            throw new NonRetryableEventException("eventType is missing or unknown");
        }
        EventType type;
        try {
            type = EventType.valueOf(typeText);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableEventException("eventType is missing or unknown", exception);
        }
        if (type != EventType.PUBLISHER_UPSERTED && type != EventType.INSTRUCTOR_UPDATED) {
            throw new NonRetryableEventException("eventType " + type + " does not belong to this topic");
        }

        if (type == EventType.PUBLISHER_UPSERTED) {
            return validatePublisher(raw);
        }
        return validateInstructor(raw);
    }

    private PublisherEvent validatePublisher(String raw) {
        EventEnvelope<PublisherEventPayload> envelope = parse(raw, PublisherEventPayload.class);
        requireEnvelope(envelope);
        PublisherEventPayload payload = envelope.payload();
        if (payload == null) {
            throw new NonRetryableEventException("payload is missing");
        }
        if (!envelope.tenantId().equals(payload.publisherId())) {
            throw new NonRetryableEventException(
                    "payload.publisherId must equal the envelope tenantId");
        }
        requireText(payload.slug(), "payload.slug");
        requireText(payload.name(), "payload.name");
        requireText(payload.type(), "payload.type");
        requireText(payload.status(), "payload.status");
        if (!"INDIVIDUAL".equals(payload.type()) && !"ORGANIZATION".equals(payload.type())) {
            throw new NonRetryableEventException("payload.type is unknown");
        }
        if (!"ACTIVE".equals(payload.status()) && !"SUSPENDED".equals(payload.status())) {
            throw new NonRetryableEventException("payload.status is unknown");
        }
        return new PublisherEvent(envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                envelope.tenantId(), payload, null);
    }

    private PublisherEvent validateInstructor(String raw) {
        EventEnvelope<InstructorEventPayload> envelope = parse(raw, InstructorEventPayload.class);
        requireEnvelope(envelope);
        InstructorEventPayload payload = envelope.payload();
        if (payload == null) {
            throw new NonRetryableEventException("payload is missing");
        }
        if (payload.userId() <= 0) {
            throw new NonRetryableEventException("payload.userId must be positive");
        }
        requireText(payload.name(), "payload.name");
        requireText(payload.tenantId(), "payload.tenantId");
        if (!envelope.tenantId().equals(payload.tenantId())) {
            throw new NonRetryableEventException(
                    "payload.tenantId must equal the envelope tenantId");
        }
        return new PublisherEvent(envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                envelope.tenantId(), null, payload);
    }

    private static <T> EventEnvelope<T> parse(String raw, Class<T> payloadType) {
        try {
            return EventJson.fromJson(raw, payloadType);
        } catch (RuntimeException exception) {
            throw new NonRetryableEventException("malformed event payload", exception);
        }
    }

    private static void requireEnvelope(EventEnvelope<?> envelope) {
        if (envelope == null) {
            throw new NonRetryableEventException("event envelope is missing");
        }
        UUID eventId = requireUuid(envelope.eventId(), "eventId");
        if (eventId == null) {
            throw new NonRetryableEventException("eventId is missing");
        }
        if (envelope.eventType() == null) {
            throw new NonRetryableEventException("eventType is missing or unknown");
        }
        Instant occurredAt = envelope.occurredAt();
        if (occurredAt == null) {
            throw new NonRetryableEventException("occurredAt is missing");
        }
        UUID tenantId = requireUuid(envelope.tenantId(), "tenantId");
        if (NIL_TENANT.equals(tenantId)) {
            throw new NonRetryableEventException("tenantId is the nil UUID");
        }
    }

    private static UUID requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NonRetryableEventException(field + " is missing");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableEventException(field + " is not a valid UUID", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NonRetryableEventException(field + " is missing");
        }
    }
}
