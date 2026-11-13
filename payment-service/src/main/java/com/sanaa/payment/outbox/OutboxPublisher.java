package com.sanaa.payment.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sanaa.payment.domain.OutboxEvent;
import com.sanaa.payment.repository.OutboxEventRepository;

/**
 * Publishes unpublished outbox rows to Kafka.
 *
 * <p>The whole batch runs in one transaction that locks the selected rows
 * ({@code FOR UPDATE SKIP LOCKED}) and marks each row published right after its acknowledged
 * send. A single transaction means the batch either commits the rows that succeeded before a
 * failure or, on a crash, publishes them again: delivery is at-least-once, never exactly-once.
 *
 * <p>WHY {@link TransactionTemplate} instead of {@code @Transactional}: the batch must commit the
 * successes made before a failure. Throwing out of a proxied method would roll the whole batch
 * back and resend everything on the next tick, and the explicit template also keeps the class
 * usable outside a proxy (the concurrency test builds a second instance).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Rows per tick. Small enough to keep the lock window short, large enough to drain a burst. */
    private static final int MAX_BATCH = 50;

    /** Upper bound on the broker acknowledgement, so one stuck send cannot hold the lock forever. */
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           OutboxMetrics metrics,
                           PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Publishes one batch and returns how many rows were marked published.
     */
    public int publishBatch() {
        Integer published = transactionTemplate.execute(status -> publishLockedRows());
        return published == null ? 0 : published;
    }

    private int publishLockedRows() {
        List<OutboxEvent> rows = outboxEventRepository.lockUnpublished(MAX_BATCH);
        int published = 0;
        for (OutboxEvent row : rows) {
            if (!publish(row)) {
                // Stop instead of skipping: a later event of the same payment must never overtake
                // an earlier one, so the whole tail waits for the next tick.
                break;
            }
            row.markPublished(Instant.now());
            metrics.published();
            published++;
        }
        return published;
    }

    private boolean publish(OutboxEvent row) {
        try {
            // The key is the aggregate (payment) id, so every event of one payment lands on the
            // same partition and stays ordered. Waiting for the acknowledgement keeps the batch
            // strictly sequential.
            kafkaTemplate.send(row.getTopic(), String.valueOf(row.getAggregateId()), row.getPayload())
                    .get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            metrics.publishFailed();
            log.warn("Outbox publication interrupted at row {}; the batch stops here",
                    row.getId());
            return false;
        } catch (Exception exception) {
            metrics.publishFailed();
            // Never log the payload: it carries the student email and name.
            log.warn("Could not publish outbox row {} to {} ({}); the batch stops here and retries "
                            + "on the next tick",
                    row.getId(), row.getTopic(), exception.toString());
            return false;
        }
    }
}
