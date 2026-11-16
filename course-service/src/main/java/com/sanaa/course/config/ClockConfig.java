package com.sanaa.course.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single clock of the service.
 *
 * <p>WHY a bean instead of {@link Clock#systemUTC()} calls: the effective price and the publish
 * rule depend on "now". Tests replace this bean with a fixed clock to prove that a discount stops
 * applying at its exact end instant without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
