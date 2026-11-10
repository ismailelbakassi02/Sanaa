package com.sanaa.payment.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.event.PaymentEventPayload;
import com.sanaa.payment.domain.OutboxEvent;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.repository.OutboxEventRepository;

/**
 * Appends a payment event to the transactional outbox.
 *
 * <p>WHY the same transaction as the payment change: either both rows commit or neither does, so
 * a committed payment always has its event and a rolled-back review never emits one. The poller
 * publishes later; this service never talks to Kafka directly.
 */
@Component
public class OutboxWriter {

    public static final String TOPIC = "sanaa.payments.v1";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxWriter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * @param reason only set for rejections; it is omitted from the payload otherwise
     */
    public void append(Payment payment, EventType eventType, String reason) {
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                now,
                payment.getTenantId(),
                new PaymentEventPayload(
                        payment.getId(),
                        payment.getUserId(),
                        payment.getUserEmail(),
                        payment.getUserName(),
                        payment.getCourseId(),
                        payment.getCourseTitle(),
                        payment.getAmount().toPlainString(),
                        payment.getCurrency(),
                        reason));
        outboxEventRepository.save(new OutboxEvent(
                envelope.eventId(),
                payment.getTenantId(),
                payment.getId(),
                eventType,
                TOPIC,
                EventJson.toJson(envelope),
                now));
    }
}
