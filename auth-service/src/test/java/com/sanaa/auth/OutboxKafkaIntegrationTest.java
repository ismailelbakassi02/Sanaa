package com.sanaa.auth;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sanaa.auth.domain.OutboxEvent;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.outbox.OutboxEventRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.config.KafkaTopicConfig;

import io.micrometer.core.instrument.MeterRegistry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Outbox publication against a real MySQL and a real Kafka broker.
 *
 * <p>The poller is enabled with a short delay, so the tests observe the production path: the HTTP
 * request commits the publisher and the outbox row, the scheduled poller locks the row and the
 * producer sends it. Create and update are separate requests: the second one must add a second row,
 * not rewrite the first (the outbox is append-only until published).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxKafkaIntegrationTest {

    private static final String TOPIC = KafkaTopicConfig.PUBLISHERS_TOPIC;
    private static final String DLT_TOPIC = KafkaTopicConfig.PUBLISHERS_DLT_TOPIC;
    private static final String PASSWORD = "correct horse battery";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Path PRIVATE_KEY_PATH = writePem("PRIVATE KEY",
            KEY_PAIR.getPrivate().getEncoded());
    private static final Path PUBLIC_KEY_PATH = writePem("PUBLIC KEY",
            KEY_PAIR.getPublic().getEncoded());
    private static final Path IMAGES_DIRECTORY = createTempDirectory();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            // V2 creates the append-only audit triggers. MySQL enables binary logging by default
            // and a non-SUPER application user may not create triggers unless this flag is set.
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("auth_outbox")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.fail-fast", () -> "true");
        registry.add("sanaa.jwt.private-key-path", PRIVATE_KEY_PATH::toString);
        registry.add("sanaa.jwt.public-key-path", PUBLIC_KEY_PATH::toString);
        registry.add("sanaa.auth.refresh-cookie-secure", () -> "true");
        registry.add("sanaa.bootstrap.enabled", () -> "false");
        registry.add("sanaa.images.directory", IMAGES_DIRECTORY::toString);
        registry.add("sanaa.outbox.poller.enabled", () -> "true");
        registry.add("sanaa.outbox.poller.fixed-delay-ms", () -> "200");
        registry.add("management.server.port", () -> "0");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void publishesTheCreateAndTheUpdateAsTwoMessagesWithThePublisherTenantId() throws Exception {
        String slug = unique("kafka");
        String ownerEmail = unique("kafka-owner") + "@example.local";

        HttpResponse<String> created = createPublisher(slug, ownerEmail);
        assertThat(created.statusCode()).isEqualTo(201);
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();

        List<ConsumerRecord<String, String>> afterCreate = awaitMessages(tenant.getId(), 1,
                Duration.ofSeconds(60));
        assertThat(afterCreate).hasSize(1);
        JsonNode createEnvelope = JSON.readTree(afterCreate.getFirst().value());
        assertThat(afterCreate.getFirst().key()).isEqualTo(tenant.getId());
        assertThat(createEnvelope.get("tenantId").asString()).isEqualTo(tenant.getId());
        assertThat(createEnvelope.get("eventType").asString()).isEqualTo("PUBLISHER_UPSERTED");
        assertThat(createEnvelope.get("payload").get("slug").asString()).isEqualTo(slug);

        List<OutboxEvent> rows = outboxEventRepository.findAllByAggregateIdOrderByIdAsc(
                tenant.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getPublishedAt()).isNotNull();
        assertThat(rows.getFirst().getTopic()).isEqualTo(TOPIC);

        String token = login(ownerEmail, PASSWORD);
        HttpResponse<String> updated = putJson("/api/auth/publishers/me", Map.of(
                "name", "Studio Kafka Renommé",
                "headline", "Titre mis à jour",
                "primaryColor", "#165DFF"), token);
        assertThat(updated.statusCode()).isEqualTo(200);

        List<ConsumerRecord<String, String>> afterUpdate = awaitMessages(tenant.getId(), 2,
                Duration.ofSeconds(60));
        assertThat(afterUpdate).hasSize(2);
        JsonNode updateEnvelope = JSON.readTree(afterUpdate.get(1).value());
        assertThat(updateEnvelope.get("tenantId").asString()).isEqualTo(tenant.getId());
        assertThat(updateEnvelope.get("eventType").asString()).isEqualTo("PUBLISHER_UPSERTED");
        assertThat(updateEnvelope.get("payload").get("name").asString())
                .isEqualTo("Studio Kafka Renommé");

        // One row per change, both published in the end.
        List<OutboxEvent> bothRows = outboxEventRepository.findAllByAggregateIdOrderByIdAsc(
                tenant.getId());
        assertThat(bothRows).hasSize(2);
        assertThat(bothRows).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());

        assertThat(meterRegistry.get("sanaa.publishers.created").counter().count())
                .isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.get("sanaa.users.registered").counter().count())
                .isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("sanaa.outbox.unpublished").gauge()).isNotNull();
    }

    @Test
    void startsThePublishersTopicWithThreePartitionsAndADeadLetterTopic() throws Exception {
        awaitTopic(TOPIC);
        awaitTopic(DLT_TOPIC);

        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(properties)) {
            TopicDescription publishers = admin.describeTopics(List.of(TOPIC))
                    .allTopicNames().get(30, TimeUnit.SECONDS).get(TOPIC);
            TopicDescription dlt = admin.describeTopics(List.of(DLT_TOPIC))
                    .allTopicNames().get(30, TimeUnit.SECONDS).get(DLT_TOPIC);
            assertThat(publishers.partitions()).hasSize(3);
            assertThat(dlt.partitions()).hasSize(3);
        }
    }

    @Test
    void anUnreachableBrokerDoesNotBlockPublisherCreation() throws Exception {
        String slug = unique("paused");
        String ownerEmail = unique("paused-owner") + "@example.local";

        pauseKafka();
        HttpResponse<String> created;
        long startedAt = System.nanoTime();
        try {
            created = createPublisher(slug, ownerEmail);
        } finally {
            // Always resume, even if the request failed, so the next test still has a broker.
            unpauseKafka();
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(created.statusCode()).isEqualTo(201);
        // The API only writes MySQL; it must not wait for the outbox poller or the broker.
        assertThat(elapsedMillis).isLessThan(5_000);

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        assertThat(outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId()))
                .hasSize(1);

        List<ConsumerRecord<String, String>> messages = awaitMessages(tenant.getId(), 1,
                Duration.ofSeconds(90));
        assertThat(messages).hasSize(1);
        assertThat(JSON.readTree(messages.getFirst().value()).get("tenantId").asString())
                .isEqualTo(tenant.getId());
    }

    // ------------------------------------------------------------------------ helpers

    private void pauseKafka() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private void unpauseKafka() {
        try {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        } catch (RuntimeException ignored) {
            // The container may already be running again.
        }
    }

    private void awaitTopic(String topic) throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        Exception lastFailure = null;
        try (AdminClient admin = AdminClient.create(properties)) {
            while (System.nanoTime() < deadline) {
                try {
                    if (admin.listTopics().names().get(30, TimeUnit.SECONDS).contains(topic)) {
                        return;
                    }
                } catch (Exception exception) {
                    lastFailure = exception;
                }
                Thread.sleep(200);
            }
        }
        fail("Topic " + topic + " was not created in time", lastFailure);
    }

    private List<ConsumerRecord<String, String>> awaitMessages(String key, int expected,
                                                               Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            while (System.nanoTime() < deadline && matches.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        matches.add(record);
                    }
                }
            }
        }
        return matches;
    }

    private HttpResponse<String> createPublisher(String slug, String ownerEmail) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("publisherType", "ORGANIZATION");
        body.put("publisherName", "Studio " + slug);
        body.put("slug", slug);
        body.put("ownerFullName", "Owner " + slug);
        body.put("ownerEmail", ownerEmail);
        body.put("password", PASSWORD);
        return HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/publishers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String login(String email, String password) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                Map.of("email", email, "password", password))))
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).get("accessToken").asString();
    }

    private HttpResponse<String> putJson(String path, Object body, String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path writePem(String type, byte[] encoded) {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(encoded);
            String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type
                    + "-----\n";
            Path path = Files.createTempFile("sanaa-outbox-test-", ".pem");
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("sanaa-outbox-images");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
