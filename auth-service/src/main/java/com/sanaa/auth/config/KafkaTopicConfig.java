package com.sanaa.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics owned by auth-service.
 *
 * <p>WHY declare them instead of relying on auto-creation: auto-created topics default to one
 * partition. Three partitions keep per-publisher ordering (the key is the publisher id) while
 * allowing some parallelism.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String PUBLISHERS_TOPIC = "sanaa.publishers.v1";
    public static final String PUBLISHERS_DLT_TOPIC = "sanaa.publishers.v1.DLT";

    @Bean
    public NewTopic publishersTopic() {
        return TopicBuilder.name(PUBLISHERS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic publishersDltTopic() {
        return TopicBuilder.name(PUBLISHERS_DLT_TOPIC).partitions(3).replicas(1).build();
    }
}
