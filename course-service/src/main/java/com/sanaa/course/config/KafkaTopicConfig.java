package com.sanaa.course.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics the course-service consumers need.
 *
 * <p>The producer services also declare them; declaring the same name, partition count and
 * replication factor here is idempotent and lets course-service run in tests and in any environment
 * where it starts before the producer. The DLT must exist before a poison record arrives, otherwise
 * the recoverer cannot publish.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String PAYMENTS_TOPIC = "sanaa.payments.v1";
    public static final String PAYMENTS_DLT_TOPIC = "sanaa.payments.v1.DLT";
    public static final String PUBLISHERS_TOPIC = "sanaa.publishers.v1";
    public static final String PUBLISHERS_DLT_TOPIC = "sanaa.publishers.v1.DLT";

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(PAYMENTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsDltTopic() {
        return TopicBuilder.name(PAYMENTS_DLT_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic publishersTopic() {
        return TopicBuilder.name(PUBLISHERS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic publishersDltTopic() {
        return TopicBuilder.name(PUBLISHERS_DLT_TOPIC).partitions(3).replicas(1).build();
    }

    /** The DLT of a source topic. Keeps a poison record on the same partition and ordering. */
    public static String deadLetterTopicFor(String topic) {
        if (PAYMENTS_TOPIC.equals(topic)) {
            return PAYMENTS_DLT_TOPIC;
        }
        if (PUBLISHERS_TOPIC.equals(topic)) {
            return PUBLISHERS_DLT_TOPIC;
        }
        return topic + ".DLT";
    }
}
