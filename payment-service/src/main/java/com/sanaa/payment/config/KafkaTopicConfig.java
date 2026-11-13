package com.sanaa.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics owned by payment-service.
 *
 * <p>WHY declare them instead of relying on auto-creation: auto-created topics default to one
 * partition, and per-payment ordering would still hold with one partition but all payments would
 * share it. Three partitions keep ordering per key while allowing some parallelism.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String PAYMENTS_TOPIC = "sanaa.payments.v1";
    public static final String PAYMENTS_DLT_TOPIC = "sanaa.payments.v1.DLT";

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(PAYMENTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsDltTopic() {
        return TopicBuilder.name(PAYMENTS_DLT_TOPIC).partitions(3).replicas(1).build();
    }
}
