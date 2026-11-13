package com.sanaa.course.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.sanaa.course.event.EventProcessingMetrics;
import com.sanaa.course.event.EventProcessingOutcome;
import com.sanaa.course.event.NonRetryableEventException;

/**
 * Consumer error handling: bounded retries for transient failures, a dead letter copy for the rest.
 *
 * <p>Boot picks this {@code CommonErrorHandler} bean up for its listener container factory, so no
 * custom container factory is needed. The container's ack mode is {@code RECORD}, so the offset is
 * committed once the listener returns or the recoverer succeeds; a failed DLT publish throws and
 * leaves the offset uncommitted, which redelivers the record instead of losing it.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            EventProcessingMetrics metrics,
            @Value("${sanaa.events.retry.initial-interval-ms:500}") long initialIntervalMs,
            @Value("${sanaa.events.retry.multiplier:2.0}") double multiplier,
            @Value("${sanaa.events.retry.max-retries:3}") int maxRetries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Same partition as the source record keeps per-payment ordering in the DLT too.
                (record, exception) -> new TopicPartition(
                        KafkaTopicConfig.PAYMENTS_DLT_TOPIC, record.partition()));

        ConsumerRecordRecoverer recovererWithMetrics = (record, exception) -> {
            publishToDeadLetterTopic(recoverer, record, exception);
            metrics.record(EventProcessingOutcome.DEAD_LETTERED);
        };

        // Defaults: three retries after the first attempt, 500 ms, 1 s, 2 s. Short enough that a
        // stuck record does not stall the partition for long; tests shorten the interval.
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recovererWithMetrics, backOff);
        // Invalid JSON, a bad envelope and an unknown course can never succeed on retry.
        errorHandler.addNotRetryableExceptions(NonRetryableEventException.class);
        errorHandler.setAckAfterHandle(true);
        return errorHandler;
    }

    private void publishToDeadLetterTopic(DeadLetterPublishingRecoverer recoverer,
                                          ConsumerRecord<?, ?> record, Exception exception) {
        try {
            recoverer.accept(record, exception);
            log.warn("Event {} on {}-{} was sent to {} after: {}",
                    record.key(), record.topic(), record.partition(),
                    KafkaTopicConfig.PAYMENTS_DLT_TOPIC, exception.toString());
        } catch (RuntimeException publishFailure) {
            // Rethrowing keeps the offset uncommitted: the record is redelivered rather than lost.
            log.error("Could not send event {} on {}-{} to {}: {}",
                    record.key(), record.topic(), record.partition(),
                    KafkaTopicConfig.PAYMENTS_DLT_TOPIC, publishFailure.toString());
            throw publishFailure;
        }
    }
}
