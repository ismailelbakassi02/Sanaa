package com.sanaa.course.event;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.event.EventType;
import com.sanaa.common.event.InstructorEventPayload;
import com.sanaa.common.event.PublisherEventPayload;
import com.sanaa.course.domain.InstructorSnapshot;
import com.sanaa.course.domain.PublisherSnapshot;
import com.sanaa.course.repository.InstructorSnapshotRepository;
import com.sanaa.course.repository.PublisherSnapshotRepository;

/**
 * Applies one publisher-topic event in its own transaction.
 *
 * <p>WHY a separate bean: the listener sets the tenant before the transaction opens. The snapshot
 * rows themselves are global (no {@code @TenantId}), but the idempotency ledger and the event
 * ordering still belong to a single transaction.
 *
 * <p>Ordering uses the event time, not the receive time. A redelivered older event is ignored:
 * {@code source_occurred_at} only moves forward, so a snapshot can never regress to a stale name or
 * a stale suspended status. A placeholder is written with {@link java.time.Instant#EPOCH}, which is
 * why the first real event always wins.
 */
@Service
public class PublisherSnapshotProcessor {

    private final ProcessedEventRepository processedEventRepository;
    private final PublisherSnapshotRepository publisherSnapshotRepository;
    private final InstructorSnapshotRepository instructorSnapshotRepository;

    public PublisherSnapshotProcessor(ProcessedEventRepository processedEventRepository,
                                      PublisherSnapshotRepository publisherSnapshotRepository,
                                      InstructorSnapshotRepository instructorSnapshotRepository) {
        this.processedEventRepository = processedEventRepository;
        this.publisherSnapshotRepository = publisherSnapshotRepository;
        this.instructorSnapshotRepository = instructorSnapshotRepository;
    }

    @Transactional
    public EventProcessingOutcome process(PublisherEvent event) {
        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            return EventProcessingOutcome.DUPLICATE;
        }
        if (event.eventType() == EventType.PUBLISHER_UPSERTED) {
            return upsertPublisher(event);
        }
        if (event.eventType() == EventType.INSTRUCTOR_UPDATED) {
            return upsertInstructor(event);
        }
        throw new NonRetryableEventException(
                "event type " + event.eventType() + " does not belong to this topic");
    }

    private EventProcessingOutcome upsertPublisher(PublisherEvent event) {
        PublisherEventPayload payload = event.publisher();
        Optional<PublisherSnapshot> existing =
                publisherSnapshotRepository.findById(event.tenantId());
        if (existing.isPresent()) {
            PublisherSnapshot snapshot = existing.get();
            if (!event.occurredAt().isAfter(snapshot.getSourceOccurredAt())) {
                return EventProcessingOutcome.IGNORED;
            }
            snapshot.update(payload.slug(), payload.name(), payload.type(), payload.status(),
                    event.occurredAt());
            publisherSnapshotRepository.saveAndFlush(snapshot);
        } else {
            publisherSnapshotRepository.saveAndFlush(new PublisherSnapshot(
                    event.tenantId(), payload.slug(), payload.name(), payload.type(),
                    payload.status(), event.occurredAt()));
        }
        return EventProcessingOutcome.PROCESSED;
    }

    private EventProcessingOutcome upsertInstructor(PublisherEvent event) {
        InstructorEventPayload payload = event.instructor();
        Optional<InstructorSnapshot> existing =
                instructorSnapshotRepository.findById(payload.userId());
        if (existing.isPresent()) {
            InstructorSnapshot snapshot = existing.get();
            if (!event.occurredAt().isAfter(snapshot.getSourceOccurredAt())) {
                return EventProcessingOutcome.IGNORED;
            }
            snapshot.update(event.tenantId(), payload.name(), event.occurredAt());
            instructorSnapshotRepository.saveAndFlush(snapshot);
        } else {
            instructorSnapshotRepository.saveAndFlush(new InstructorSnapshot(
                    payload.userId(), event.tenantId(), payload.name(), event.occurredAt()));
        }
        return EventProcessingOutcome.PROCESSED;
    }
}
