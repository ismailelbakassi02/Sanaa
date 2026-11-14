package com.sanaa.auth.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sanaa.auth.domain.OutboxEvent;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.event.InstructorEventPayload;
import com.sanaa.common.event.PublisherEventPayload;

/**
 * Appends publisher events to the transactional outbox.
 *
 * <p>WHY the same transaction as the publisher change: either both rows commit or neither does, so
 * a committed publisher always has its event and a rolled-back update never emits one. The poller
 * publishes later; this service never talks to Kafka directly.
 */
@Component
public class OutboxWriter {

    public static final String TOPIC = "sanaa.publishers.v1";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxWriter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public void publisherUpserted(Tenant tenant) {
        Instant now = Instant.now();
        EventEnvelope<PublisherEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.PUBLISHER_UPSERTED,
                now,
                tenant.getId(),
                new PublisherEventPayload(
                        tenant.getId(),
                        tenant.getSlug(),
                        tenant.getName(),
                        tenant.getType().name(),
                        tenant.getStatus().name(),
                        tenant.getContactEmail(),
                        tenant.getPrimaryColor()));
        append(tenant.getId(), EventType.PUBLISHER_UPSERTED, envelope, now);
    }

    public void instructorUpdated(long userId, String tenantId, String name) {
        Instant now = Instant.now();
        EventEnvelope<InstructorEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.INSTRUCTOR_UPDATED,
                now,
                tenantId,
                new InstructorEventPayload(userId, tenantId, name));
        append(tenantId, EventType.INSTRUCTOR_UPDATED, envelope, now);
    }

    private void append(String aggregateId, EventType eventType, EventEnvelope<?> envelope,
                        Instant now) {
        outboxEventRepository.save(new OutboxEvent(
                envelope.eventId(),
                aggregateId,
                aggregateId,
                eventType,
                TOPIC,
                EventJson.toJson(envelope),
                now));
    }
}
