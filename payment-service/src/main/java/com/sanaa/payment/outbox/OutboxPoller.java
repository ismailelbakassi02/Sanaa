package com.sanaa.payment.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link OutboxPublisher} on a fixed delay.
 *
 * <p>The scheduled method never propagates an exception: a database hiccup or an unreachable
 * broker must not kill the scheduler thread. The next tick simply retries the unpublished rows.
 */
@Component
@ConditionalOnProperty(name = "sanaa.outbox.poller.enabled", havingValue = "true",
        matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPublisher publisher;

    public OutboxPoller(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${sanaa.outbox.poller.fixed-delay-ms:1000}")
    public void poll() {
        try {
            publisher.publishBatch();
        } catch (RuntimeException exception) {
            log.warn("Outbox poll failed, retrying on the next tick: {}", exception.toString());
        }
    }
}
