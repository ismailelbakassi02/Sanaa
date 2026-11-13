package com.sanaa.course.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics the course-service consumer needs.
 *
 * <p>The payment service also declares them; declaring the same name, partition count and
 * replication factor here is idempotent and lets course-service run in tests and in any
 * environment where it starts before the producer. The DLT must exist before a poison record
 * arrives, otherwise the recoverer cannot publish.
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
