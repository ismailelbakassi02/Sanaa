package com.sanaa.course.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventType;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.config.KafkaTopicConfig;

/**
 * Creates enrollments from approved payments.
 *
 * <p>The listener itself is not transactional. It validates the event, skips event types it does
 * not own, and calls a separate {@code @Transactional} bean under the tenant from the envelope.
 * Setting the context before that call is what makes {@code @TenantId} work: Hibernate resolves the
 * tenant when the session opens, which happens inside the proxied call.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentEventValidator validator;
    private final EnrollmentEventProcessor processor;
    private final EventProcessingMetrics metrics;

    public PaymentEventConsumer(PaymentEventValidator validator,
                                EnrollmentEventProcessor processor,
                                EventProcessingMetrics metrics) {
        this.validator = validator;
        this.processor = processor;
        this.metrics = metrics;
    }

    @KafkaListener(topics = KafkaTopicConfig.PAYMENTS_TOPIC, groupId = "course-service")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = validator.validate(record.value());

        if (envelope.eventType() != EventType.PAYMENT_APPROVED) {
            // Submitted and rejected events are valid traffic for this topic but create nothing.
            // Returning normally commits the offset. No payload is ever logged.
            log.debug("Ignoring {} event {}", envelope.eventType(), envelope.eventId());
            metrics.record(EventProcessingOutcome.IGNORED);
            return;
        }

        // WHY here and not inside the processor: TenantContext must be populated before the
        // transaction opens. callAs restores (clears) the previous value in a finally block.
        EventProcessingOutcome outcome = TenantContext.callAs(envelope.tenantId(),
                () -> processor.process(envelope));
        metrics.record(outcome);
    }
}
