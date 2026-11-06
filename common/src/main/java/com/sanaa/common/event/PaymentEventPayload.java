package com.sanaa.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payment data carried inside an {@link EventEnvelope}.
 *
 * <p>{@code amount} is a string so the wire format is exactly the decimal value from the database,
 * and {@code reason} is only present for rejected payments. Null fields are omitted to keep the
 * wire contract identical to the example in {@code docs/architecture.md}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentEventPayload(
        long paymentId,
        long userId,
        String userEmail,
        String userName,
        long courseId,
        String courseTitle,
        String amount,
        String currency,
        String reason
) {
}
