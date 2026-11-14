package com.sanaa.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * Business counters.
 *
 * <p>No tenant tag: publisher identity must not leak into the metrics backend. Prometheus exposes
 * a Micrometer counter named {@code sanaa.publishers.created} as
 * {@code sanaa_publishers_created_total}.
 */
@Component
public class AuthMetrics {

    private final Counter publishersCreated;
    private final Counter usersRegistered;

    public AuthMetrics(MeterRegistry registry) {
        this.publishersCreated = Counter.builder("sanaa.publishers.created")
                .description("Publishers created")
                .register(registry);
        this.usersRegistered = Counter.builder("sanaa.users.registered")
                .description("Global accounts registered through the public form")
                .register(registry);
    }

    public void publisherCreated() {
        publishersCreated.increment();
    }

    public void userRegistered() {
        usersRegistered.increment();
    }
}
