package com.sanaa.course.event;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.PaymentEventPayload;
import com.sanaa.common.tenant.TenantContext;

/**
 * Parses and validates an event before any database work.
 *
 * <p>Every failure here is non-retryable: the same bytes would fail again on every attempt, so the
 * record belongs in the dead letter topic where an operator can inspect it.
 */
@Component
public class PaymentEventValidator {

    private static final UUID NIL_TENANT = UUID.fromString(TenantContext.NIL_TENANT);

    public EventEnvelope<PaymentEventPayload> validate(String raw) {
        EventEnvelope<PaymentEventPayload> envelope;
        try {
            envelope = EventJson.fromJson(raw, PaymentEventPayload.class);
        } catch (RuntimeException exception) {
            // Jackson 3 throws unchecked exceptions; the original message may echo the payload,
            // so only the exception type is propagated as the cause.
            throw new NonRetryableEventException("malformed event JSON", exception);
        }

        if (envelope == null) {
            throw new NonRetryableEventException("event envelope is missing");
        }
        requireUuid(envelope.eventId(), "eventId");
        if (envelope.eventType() == null) {
            throw new NonRetryableEventException("eventType is missing or unknown");
        }
        if (envelope.occurredAt() == null) {
            throw new NonRetryableEventException("occurredAt is missing");
        }

        UUID tenantId = requireUuid(envelope.tenantId(), "tenantId");
        if (NIL_TENANT.equals(tenantId)) {
            throw new NonRetryableEventException("tenantId is the nil UUID");
        }

        PaymentEventPayload payload = envelope.payload();
        if (payload == null) {
            throw new NonRetryableEventException("payload is missing");
        }
        if (payload.paymentId() <= 0) {
            throw new NonRetryableEventException("payload.paymentId must be positive");
        }
        if (payload.userId() <= 0) {
            throw new NonRetryableEventException("payload.userId must be positive");
        }
        if (payload.courseId() <= 0) {
            throw new NonRetryableEventException("payload.courseId must be positive");
        }
        requireText(payload.userEmail(), "payload.userEmail");
        requireText(payload.userName(), "payload.userName");
        requireText(payload.courseTitle(), "payload.courseTitle");
        requireText(payload.amount(), "payload.amount");
        requireText(payload.currency(), "payload.currency");

        return envelope;
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
