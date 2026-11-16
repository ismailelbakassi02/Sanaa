package com.sanaa.course.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.config.KafkaTopicConfig;

/**
 * Refreshes the cross-tenant publisher and instructor snapshots.
 *
 * <p>The listener is not transactional. It validates the record, sets the tenant from the event
 * envelope, and calls a separate {@code @Transactional} bean. The context is set before that call
 * so it exists before the Hibernate session opens. No payload is ever logged: it contains names.
 */
@Component
public class PublisherEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PublisherEventConsumer.class);

    private final PublisherEventValidator validator;
    private final PublisherSnapshotProcessor processor;
    private final EventProcessingMetrics metrics;

    public PublisherEventConsumer(PublisherEventValidator validator,
                                  PublisherSnapshotProcessor processor,
                                  EventProcessingMetrics metrics) {
        this.validator = validator;
        this.processor = processor;
        this.metrics = metrics;
    }

    @KafkaListener(topics = KafkaTopicConfig.PUBLISHERS_TOPIC, groupId = "course-service")
    public void onPublisherEvent(ConsumerRecord<String, String> record) {
        PublisherEvent event = validator.validate(record.value());
        EventProcessingOutcome outcome = TenantContext.callAs(event.tenantId(),
                () -> processor.process(event));
        if (outcome == EventProcessingOutcome.IGNORED) {
            log.debug("Ignored stale {} event {}", event.eventType(), event.eventId());
        }
        metrics.record(outcome);
    }
}
