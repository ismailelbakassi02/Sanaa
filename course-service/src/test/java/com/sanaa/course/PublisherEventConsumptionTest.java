package com.sanaa.course;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.event.InstructorEventPayload;
import com.sanaa.common.event.PublisherEventPayload;
import com.sanaa.course.config.KafkaTopicConfig;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.PublisherSnapshot;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code sanaa.publishers.v1} consumer against a real Kafka broker and MySQL.
 *
 * <p>Proves the snapshot contract of the catalogue: a publisher event is an upsert ordered by the
 * event time, a placeholder is replaced by the first real event, and a suspension removes every
 * course of that publisher from the anonymous pages. Poison records must land on the topic-specific
 * dead letter topic without creating anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class PublisherEventConsumptionTest extends AbstractCourseApiTest {

    private static final String PUBLISHERS_TOPIC = KafkaTopicConfig.PUBLISHERS_TOPIC;
    private static final String DLT_TOPIC = KafkaTopicConfig.PUBLISHERS_DLT_TOPIC;

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_publisher_events_test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // The transient-failure path is not exercised here; a short retry keeps the DLT cases fast.
        registry.add("sanaa.events.retry.initial-interval-ms", () -> "50");
        registry.add("sanaa.events.retry.max-retries", () -> "3");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    // ---------------------------------------------------------------- upserts

    @Test
    void publisherUpsertMakesAPublishedCourseVisible() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours du nouvel éditeur",
                CourseStatus.PUBLISHED);

        // No snapshot yet: the visibility rule joins an ACTIVE publisher, so the page is a 404.
        assertThat(get("/api/public/courses/" + course.getId(), null).status()).isEqualTo(404);

        sendPublisher(tenantId, "atlas-academy", "Atlas Academy", "ORGANIZATION", "ACTIVE",
                Instant.now());

        await(Duration.ofSeconds(20), () -> getQuietly("/api/public/courses/" + course.getId())
                == 200);
        HttpResult page = get("/api/public/courses/" + course.getId(), null);
        assertThat(page.status()).isEqualTo(200);
        JsonNode body = json(page);
        assertThat(body.get("publisherName").asString()).isEqualTo("Atlas Academy");
        assertThat(body.get("publisherSlug").asString()).isEqualTo("atlas-academy");
        assertThat(body.get("publisherType").asString()).isEqualTo("ORGANIZATION");
        assertThat(counter("processed")).isGreaterThanOrEqualTo(1);

        String stored = jdbcTemplate.queryForObject(
                "SELECT name FROM publisher_snapshots WHERE tenant_id = ?", String.class, tenantId);
        assertThat(stored).isEqualTo("Atlas Academy");
    }

    @Test
    void instructorUpdateRefreshesTheNameOnTheCoursePage() throws Exception {
        String tenantId = newTenantId();
        Course course = publishVisibleCourse(tenantId, 300, "Cours de Nadia",
                new BigDecimal("249.00"));

        sendInstructor(tenantId, 300, "Nadia Bennani", Instant.now().plusSeconds(5));

        await(Duration.ofSeconds(20), () -> {
            Probe page = getQuietlyResult("/api/public/courses/" + course.getId());
            return page.status() == 200 && page.body().contains("Nadia Bennani");
        });
        assertThat(get("/api/public/courses/" + course.getId(), null).body())
                .contains("Nadia Bennani")
                .doesNotContain("Formateur 300");
    }

    @Test
    void staleRedeliveryNeverOverwritesANewerSnapshot() throws Exception {
        String tenantId = newTenantId();
        Instant now = Instant.now();
        savePublisherSnapshot(tenantId, "atlas", "Nom récent", "ORGANIZATION", "ACTIVE", now);
        double ignoredBefore = counter("ignored");

        sendPublisher(tenantId, "atlas", "Nom obsolète", "ORGANIZATION", "SUSPENDED",
                now.minusSeconds(3600));

        await(Duration.ofSeconds(20), () -> counter("ignored") >= ignoredBefore + 1);
        PublisherSnapshot snapshot = publisherSnapshotRepository.findById(tenantId).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("Nom récent");
        assertThat(snapshot.getStatus()).isEqualTo("ACTIVE");
        // MySQL DATETIME(6) stores microseconds and rounds the trailing nanosecond, so the
        // round-trip is compared to the millisecond rather than to the exact Instant.
        assertThat(snapshot.getSourceOccurredAt())
                .isBetween(now.minusMillis(1), now.plusMillis(1));
    }

    @Test
    void theFirstRealEventReplacesTheStudioPlaceholder() throws Exception {
        String tenantId = newTenantId();
        // The studio writes a placeholder with EPOCH so the course is joinable before the event.
        savePublisherSnapshot(tenantId, "placeholder", "Placeholder", "INDIVIDUAL", "ACTIVE",
                Instant.EPOCH);

        sendPublisher(tenantId, "studio-acme", "Studio Acme", "ORGANIZATION", "ACTIVE",
                Instant.now());

        await(Duration.ofSeconds(20), () -> "Studio Acme".equals(
                publisherSnapshotRepository.findById(tenantId).map(PublisherSnapshot::getName)
                        .orElse(null)));
        PublisherSnapshot snapshot = publisherSnapshotRepository.findById(tenantId).orElseThrow();
        assertThat(snapshot.getSlug()).isEqualTo("studio-acme");
        assertThat(snapshot.getType()).isEqualTo("ORGANIZATION");
        assertThat(snapshot.getSourceOccurredAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void aRedeliveredEventIdIsAppliedOnce() throws Exception {
        String tenantId = newTenantId();
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();
        String payload = publisherEvent(eventId, tenantId, "atlas", "Atlas Academy",
                "ORGANIZATION", "ACTIVE", occurredAt);
        double processedBefore = counter("processed");
        double duplicateBefore = counter("duplicate");

        send(tenantId, payload);
        send(tenantId, payload);

        await(Duration.ofSeconds(20), () -> counter("duplicate") >= duplicateBefore + 1);
        assertThat(counter("processed")).isGreaterThanOrEqualTo(processedBefore + 1);
        Long ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, eventId);
        assertThat(ledgerRows).isEqualTo(1);
        assertThat(publisherSnapshotRepository.findById(tenantId)).isPresent();
    }

    @Test
    void aSuspendedPublisherDisappearsThenComesBackOnReactivation() throws Exception {
        String tenantId = newTenantId();
        Course course = publishVisibleCourse(tenantId, 310, "Cours suspendu",
                new BigDecimal("199.00"));
        assertThat(get("/api/public/courses/" + course.getId(), null).status()).isEqualTo(200);

        sendPublisher(tenantId, "atlas", "Atlas Academy", "ORGANIZATION", "SUSPENDED",
                Instant.now().plusSeconds(5));
        await(Duration.ofSeconds(20),
                () -> getQuietly("/api/public/courses/" + course.getId()) == 404);
        assertThat(get("/api/public/courses/" + course.getId(), null).status()).isEqualTo(404);

        sendPublisher(tenantId, "atlas", "Atlas Academy", "ORGANIZATION", "ACTIVE",
                Instant.now().plusSeconds(10));
        await(Duration.ofSeconds(20),
                () -> getQuietly("/api/public/courses/" + course.getId()) == 200);
        assertThat(get("/api/public/courses/" + course.getId(), null).status()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- poison records

    @Test
    void malformedJsonGoesToThePublisherDeadLetterTopic() throws Exception {
        assertDeadLettered("poison-" + UUID.randomUUID(), "{ \"eventId\": ");
    }

    @Test
    void aPaymentEventOnThePublisherTopicIsDeadLettered() throws Exception {
        String tenantId = newTenantId();
        String raw = EventJson.toJson(new EventEnvelope<>(
                UUID.randomUUID().toString(), EventType.PAYMENT_APPROVED, Instant.now(), tenantId,
                new PublisherEventPayload(tenantId, "atlas", "Atlas", "ORGANIZATION", "ACTIVE",
                        null, null)));

        assertDeadLettered("wrong-type-" + UUID.randomUUID(), raw);
        assertThat(publisherSnapshotRepository.findById(tenantId)).isEmpty();
    }

    @Test
    void aPayloadWhosePublisherIdDiffersFromTheEnvelopeIsDeadLettered() throws Exception {
        String tenantId = newTenantId();
        String raw = EventJson.toJson(new EventEnvelope<>(
                UUID.randomUUID().toString(), EventType.PUBLISHER_UPSERTED, Instant.now(), tenantId,
                new PublisherEventPayload(UUID.randomUUID().toString(), "atlas", "Atlas",
                        "ORGANIZATION", "ACTIVE", null, null)));

        assertDeadLettered("mismatch-" + UUID.randomUUID(), raw);
        assertThat(publisherSnapshotRepository.findById(tenantId)).isEmpty();
    }

    @Test
    void aPublisherEventWithoutANameIsDeadLettered() throws Exception {
        String tenantId = newTenantId();
        String eventId = UUID.randomUUID().toString();
        String raw = EventJson.toJson(new EventEnvelope<>(
                eventId, EventType.PUBLISHER_UPSERTED, Instant.now(), tenantId,
                new PublisherEventPayload(tenantId, "atlas", null, "ORGANIZATION", "ACTIVE",
                        null, null)));

        assertDeadLettered(tenantId, raw);
        assertThat(publisherSnapshotRepository.findById(tenantId)).isEmpty();
        Long ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, eventId);
        assertThat(ledgerRows).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private void sendPublisher(String tenantId, String slug, String name, String type,
                               String status, Instant occurredAt) throws Exception {
        send(tenantId, publisherEvent(UUID.randomUUID().toString(), tenantId, slug, name, type,
                status, occurredAt));
    }

    private String publisherEvent(String eventId, String tenantId, String slug, String name,
                                  String type, String status, Instant occurredAt) {
        return EventJson.toJson(new EventEnvelope<>(
                eventId,
                EventType.PUBLISHER_UPSERTED,
                occurredAt,
                tenantId,
                new PublisherEventPayload(tenantId, slug, name, type, status,
                        "contact@atlas.test", "#165DFF")));
    }

    private void sendInstructor(String tenantId, long userId, String name, Instant occurredAt)
            throws Exception {
        send(tenantId, EventJson.toJson(new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EventType.INSTRUCTOR_UPDATED,
                occurredAt,
                tenantId,
                new InstructorEventPayload(userId, tenantId, name))));
    }

    private void send(String key, String payload) throws Exception {
        kafkaTemplate.send(PUBLISHERS_TOPIC, key, payload).get(10, TimeUnit.SECONDS);
    }

    /**
     * Sends a record that must end up on the publisher DLT, then asserts it arrived once and that
     * the consumer recorded the dead-letter outcome.
     */
    private void assertDeadLettered(String key, String payload) throws Exception {
        double deadLetteredBefore = counter("dead_lettered");

        send(key, payload);

        List<ConsumerRecord<String, String>> records = awaitDlt(key, 1, Duration.ofSeconds(15));
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().value()).isEqualTo(payload);
        assertThat(records.getFirst().topic()).isEqualTo(DLT_TOPIC);
        assertThat(counter("dead_lettered")).isGreaterThanOrEqualTo(deadLetteredBefore + 1);
    }

    private List<ConsumerRecord<String, String>> awaitDlt(String key, int expected,
                                                          Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "publisher-dlt-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");

        long deadline = System.nanoTime() + timeout.toNanos();
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DLT_TOPIC));
            while (System.nanoTime() < deadline && found.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    private int getQuietly(String path) {
        try {
            return get(path, null).status();
        } catch (Exception exception) {
            return -1;
        }
    }

    private Probe getQuietlyResult(String path) {
        try {
            HttpResult result = get(path, null);
            return new Probe(result.status(), result.body());
        } catch (Exception exception) {
            return new Probe(-1, "");
        }
    }

    private double counter(String result) {
        Counter counter = meterRegistry.find("sanaa.events.processed").tag("result", result)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    /** PublisherSnapshot always needs an explicit event time, so this helper overrides the base. */
    private PublisherSnapshot savePublisherSnapshot(String tenantId, String slug, String name,
                                                   String type, String status,
                                                   Instant sourceOccurredAt) {
        return publisherSnapshotRepository.saveAndFlush(
                new PublisherSnapshot(tenantId, slug, name, type, status, sourceOccurredAt));
    }

    private static void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }

    /** Local copy of the shared HTTP result, because the base record's constructor is protected. */
    private record Probe(int status, String body) {
    }
}
