package com.sanaa.payment;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.PaymentEventPayload;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.payment.domain.OutboxEvent;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentMethod;
import com.sanaa.payment.outbox.OutboxMetrics;
import com.sanaa.payment.outbox.OutboxPublisher;
import com.sanaa.payment.repository.OutboxEventRepository;
import com.sanaa.payment.repository.PaymentRepository;
import com.sanaa.payment.service.OutboxWriter;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox publication against a real MySQL and a real Kafka broker.
 *
 * <p>Payments are seeded through the repositories (the course-service stub is exercised by
 * {@link PaymentIntegrationTest}); the review itself goes through the HTTP API so the status code
 * and the outbox write are the production ones. The poller runs on a 200 ms delay here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(OutboxPublishingIntegrationTest.JwtTestConfiguration.class)
class OutboxPublishingIntegrationTest {

    private static final String TOPIC = "sanaa.payments.v1";
    private static final String ISSUER = "sanaa-auth";
    private static final String AUDIENCE = "sanaa-api";
    private static final String KEY_ID = "test-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Path RECEIPTS = createReceiptsDirectory();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("payment_outbox_test")
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
        registry.add("management.server.port", () -> "0");
        registry.add("sanaa.payment.receipts-dir", () -> RECEIPTS.toString());
        registry.add("sanaa.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
        registry.add("sanaa.outbox.poller.enabled", () -> "true");
        registry.add("sanaa.outbox.poller.fixed-delay-ms", () -> "200");
        // No idle-connection replenisher: it keeps retrying while the context closes at JVM exit,
        // which makes surefire wait for its 30 s fork timeout.
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxMetrics outboxMetrics;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private TransactionTemplate transactionTemplate;

    private TransactionTemplate transactionTemplate() {
        if (transactionTemplate == null) {
            transactionTemplate = new TransactionTemplate(transactionManager);
        }
        return transactionTemplate;
    }

    // ---------------------------------------------------------------- publication

    @Test
    void approvingPublishesExactlyOneApprovedMessageAndMarksTheRowPublished() throws Exception {
        String tenantId = newTenantId();
        long paymentId = seedPayment(tenantId, 201, 9201);
        String key = Long.toString(paymentId);

        assertThat(approve(paymentId, token(tenantId, 900, Role.TENANT_ADMIN))).isEqualTo(200);

        List<ConsumerRecord<String, String>> messages = awaitMessages(key, 2, Duration.ofSeconds(40));
        List<ConsumerRecord<String, String>> approved = messages.stream()
                .filter(record -> eventType(record).equals(EventType.PAYMENT_APPROVED.name()))
                .toList();
        assertThat(approved).hasSize(1);

        EventEnvelope<PaymentEventPayload> envelope = EventJson.fromJson(
                approved.getFirst().value(), PaymentEventPayload.class);
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.eventType()).isEqualTo(EventType.PAYMENT_APPROVED);
        assertThat(envelope.payload().paymentId()).isEqualTo(paymentId);
        assertThat(envelope.payload().userId()).isEqualTo(201);
        assertThat(envelope.payload().courseId()).isEqualTo(9201);
        assertThat(envelope.payload().amount()).isEqualTo("199.00");

        await(Duration.ofSeconds(20), () -> outboxFor(tenantId, paymentId).stream()
                .allMatch(event -> event.getPublishedAt() != null));
    }

    @Test
    void eventsOfOnePaymentArriveInOrderOnTheSamePartition() throws Exception {
        String tenantId = newTenantId();
        long paymentId = seedPayment(tenantId, 202, 9202);
        String key = Long.toString(paymentId);

        assertThat(approve(paymentId, token(tenantId, 900, Role.TENANT_ADMIN))).isEqualTo(200);

        List<String> types = awaitMessages(key, 2, Duration.ofSeconds(40)).stream()
                .map(this::eventType)
                .toList();
        assertThat(types).containsExactly(
                EventType.PAYMENT_SUBMITTED.name(), EventType.PAYMENT_APPROVED.name());

        List<ConsumerRecord<String, String>> messages = awaitMessages(key, 2, Duration.ofSeconds(5));
        assertThat(messages).extracting(ConsumerRecord::partition).containsOnly(
                messages.getFirst().partition());
    }

    @Test
    void approvingTwiceIsRejectedAndPublishesNoSecondMessage() throws Exception {
        String tenantId = newTenantId();
        long paymentId = seedPayment(tenantId, 203, 9203);
        String key = Long.toString(paymentId);
        String adminToken = token(tenantId, 900, Role.TENANT_ADMIN);

        assertThat(approve(paymentId, adminToken)).isEqualTo(200);
        assertThat(approve(paymentId, adminToken)).isEqualTo(409);

        List<ConsumerRecord<String, String>> messages = awaitMessages(key, 2, Duration.ofSeconds(40));
        assertThat(messages).hasSize(2);
        assertThat(messages.stream()
                .filter(record -> eventType(record).equals(EventType.PAYMENT_APPROVED.name()))
                .count()).isEqualTo(1);
    }

    @Test
    void twoPollersNeverPublishTheSameRowTwice() throws Exception {
        String tenantId = newTenantId();
        List<Long> paymentIds = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            paymentIds.add(seedPayment(tenantId, 210, 9300 + index));
        }

        OutboxPublisher secondInstance = new OutboxPublisher(
                outboxEventRepository, kafkaTemplate, outboxMetrics, transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return new OutboxPublisher(outboxEventRepository, kafkaTemplate, outboxMetrics,
                        transactionManager).publishBatch();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                return secondInstance.publishBatch();
            });
            start.countDown();
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // The scheduled poller of the context may also take part: the invariant is that no row is
        // published twice, whichever instance wins the lock.
        for (Long paymentId : paymentIds) {
            String key = Long.toString(paymentId);
            awaitMessages(key, 1, Duration.ofSeconds(40));
        }
        for (Long paymentId : paymentIds) {
            String key = Long.toString(paymentId);
            List<ConsumerRecord<String, String>> messages =
                    awaitMessages(key, 1, Duration.ofSeconds(2));
            assertThat(messages).as("messages of payment %s", paymentId).hasSize(1);
        }
        assertThat(outboxFor(tenantId, paymentIds).stream()
                .allMatch(event -> event.getPublishedAt() != null)).isTrue();
    }

    // ---------------------------------------------------------------- broker down

    @Test
    void approvalSucceedsWhileTheBrokerIsPausedAndTheRowIsPublishedAfterUnpause() throws Exception {
        String tenantId = newTenantId();
        long paymentId = seedPayment(tenantId, 204, 9204);
        String key = Long.toString(paymentId);

        // Let the submission event drain so the only unpublished row is the approval.
        await(Duration.ofSeconds(40), () -> outboxFor(tenantId, paymentId).stream()
                .allMatch(event -> event.getPublishedAt() != null));

        pauseBroker();
        try {
            assertThat(approve(paymentId, token(tenantId, 900, Role.TENANT_ADMIN))).isEqualTo(200);

            await(Duration.ofSeconds(10), () -> outboxFor(tenantId, paymentId).stream()
                    .anyMatch(event -> event.getPublishedAt() == null));
            assertThat(meterRegistry.get("sanaa.outbox.unpublished").gauge().value())
                    .isGreaterThan(0);
        } finally {
            resumeBroker();
        }

        await(Duration.ofSeconds(60), () -> outboxFor(tenantId, paymentId).stream()
                .allMatch(event -> event.getPublishedAt() != null));
        List<ConsumerRecord<String, String>> messages = awaitMessages(key, 2, Duration.ofSeconds(60));
        assertThat(messages.stream()
                .filter(record -> eventType(record).equals(EventType.PAYMENT_APPROVED.name()))
                .count()).isEqualTo(1);
    }

    @Test
    void startsAndServesHealthWhileTheBrokerIsPaused() throws Exception {
        pauseBroker();
        ConfigurableApplicationContext context = null;
        try {
            // Command-line arguments, not default properties: application.yml must not win.
            context = new SpringApplicationBuilder(PaymentServiceApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run("--server.port=0",
                            "--management.server.port=0",
                            "--spring.datasource.url=" + MYSQL.getJdbcUrl()
                                    + "?useSSL=false&allowPublicKeyRetrieval=true",
                            "--spring.datasource.username=" + MYSQL.getUsername(),
                            "--spring.datasource.password=" + MYSQL.getPassword(),
                            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                            "--sanaa.jwt.jwks-uri=http://127.0.0.1:1/.well-known/jwks.json",
                            "--sanaa.payment.receipts-dir=" + RECEIPTS,
                            // The point is startup and HTTP, not a second publisher.
                            "--sanaa.outbox.poller.enabled=false");

            int healthPort = Integer.parseInt(
                    context.getEnvironment().getProperty("local.management.port"));
            HttpResponse<String> health = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + healthPort
                            + "/actuator/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"");
        } finally {
            if (context != null) {
                SpringApplication.exit(context);
                context.close();
            }
            resumeBroker();
        }
    }

    @Test
    void prometheusExposesTheOutboxMetricsWithoutTenantTags() throws Exception {
        HttpResponse<String> metrics = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort
                        + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("sanaa_outbox_unpublished");
        assertThat(metrics.body()).contains("sanaa_outbox_published_total");
        assertThat(metrics.body()).contains("sanaa_outbox_publish_failures_total");
        assertThat(metrics.body()).contains("application=\"payment-service\"");
        assertThat(metrics.body()).doesNotContain("tenant");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Creates a pending payment and its PAYMENT_SUBMITTED outbox row, exactly like the submission
     * endpoint would, without depending on course-service.
     */
    private long seedPayment(String tenantId, long userId, long courseId) {
        return TenantContext.callAs(tenantId, () -> transactionTemplate().execute(status -> {
            Payment payment = new Payment(userId, "user" + userId + "@example.test",
                    "User " + userId, courseId, "Cours " + courseId,
                    new BigDecimal("199.00"), "MAD", PaymentMethod.BANK_WIRE,
                    tenantId + "/receipt", "image/png");
            paymentRepository.saveAndFlush(payment);
            outboxWriter.append(payment, EventType.PAYMENT_SUBMITTED, null);
            return payment.getId();
        }));
    }

    private List<OutboxEvent> outboxFor(String tenantId, long paymentId) {
        return TenantContext.callAs(tenantId,
                () -> outboxEventRepository.findAllByAggregateIdOrderByIdAsc(paymentId));
    }

    private List<OutboxEvent> outboxFor(String tenantId, List<Long> paymentIds) {
        List<OutboxEvent> events = new ArrayList<>();
        for (Long paymentId : paymentIds) {
            events.addAll(outboxFor(tenantId, paymentId));
        }
        return events;
    }

    private List<ConsumerRecord<String, String>> awaitMessages(String key, int expected,
                                                               Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-test-" + UUID.randomUUID());
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
            consumer.subscribe(List.of(TOPIC));
            while (System.nanoTime() < deadline && found.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    private String eventType(ConsumerRecord<String, String> record) {
        return JSON.readTree(record.value()).get("eventType").asString();
    }

    private int approve(long paymentId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/payments/" + paymentId
                                + "/approve"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
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

    private static void pauseBroker() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void resumeBroker() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private String token(String tenantId, long userId, Role role) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER)
                            .audience(AUDIENCE)
                            .subject(Long.toString(userId))
                            .issueTime(Date.from(Instant.now()))
                            .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                            .jwtID(UUID.randomUUID().toString())
                            .claim("role", role.name())
                            .claim("email", "user" + userId + "@example.test")
                            .claim("name", "User " + userId)
                            .claim("tenant_id", tenantId)
                            .build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String newTenantId() {
        return UUID.randomUUID().toString();
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

    private static Path createReceiptsDirectory() {
        try {
            return Files.createTempDirectory("sanaa-payment-outbox-receipts");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration
    static class JwtTestConfiguration {

        private static final OAuth2TokenValidator<Jwt> AUDIENCE_VALIDATOR = token ->
                token.getAudience().contains(AUDIENCE)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "The required audience is missing.", null));

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                    (RSAPublicKey) KEY_PAIR.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER), AUDIENCE_VALIDATOR));
            return decoder;
        }
    }
}
