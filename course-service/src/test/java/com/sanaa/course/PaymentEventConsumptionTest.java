package com.sanaa.course;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.event.PaymentEventPayload;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.event.ProcessedEventRepository;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.EnrollmentRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * course-service consumer tests against a real MySQL and a real Kafka broker.
 *
 * <p>Events are produced with the application's own String template, exactly as payment-service
 * publishes them. The dead letter topic is read with a throwaway consumer group. The retry backoff
 * is shortened so the transient-failure case stays fast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(PaymentEventConsumptionTest.JwtTestConfiguration.class)
class PaymentEventConsumptionTest {

    private static final String PAYMENTS_TOPIC = "sanaa.payments.v1";
    private static final String DLT_TOPIC = "sanaa.payments.v1.DLT";
    private static final String ISSUER = "sanaa-auth";
    private static final String AUDIENCE = "sanaa-api";
    private static final String KEY_ID = "test-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("course_events_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // SSL is disabled only because the container certificate can be ahead of the host clock on
        // Windows, which makes the driver reject the handshake.
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("management.server.port", () -> "0");
        registry.add("sanaa.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
        // Production defaults are 500 ms, x2, three retries; the transient test does not need to
        // wait that long.
        registry.add("sanaa.events.retry.initial-interval-ms", () -> "50");
        registry.add("sanaa.events.retry.max-retries", () -> "3");
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean
    private ProcessedEventRepository processedEventRepository;

    // ---------------------------------------------------------------- enrollment creation

    @Test
    void approvedPaymentCreatesOneActiveEnrollmentVisibleOnlyInItsTenant() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        long userId = 501;
        long paymentId = 77_001;
        Course course = saveCourse(tenantA, 100, "Cours payé par virement", CourseStatus.PUBLISHED);
        String eventId = UUID.randomUUID().toString();
        double processedBefore = counter("processed");

        send(paymentId, approvedEvent(eventId, tenantA, paymentId, userId, course.getId()));

        Enrollment enrollment = awaitEnrollment(tenantA, userId, course.getId());
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollment.getSourcePaymentId()).isEqualTo(paymentId);
        assertThat(enrollment.getTenantId()).isEqualTo(tenantA);
        assertThat(awaitEnrollmentCount(tenantA, userId, course.getId(), 1)).isTrue();
        await(Duration.ofSeconds(10), () -> counter("processed") >= processedBefore + 1);

        HttpResult mine = get("/api/enrollments/me", token(tenantA, userId, Role.STUDENT));
        assertThat(mine.status()).isEqualTo(200);
        JsonNode items = json(mine);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(items.get(0).get("course").get("title").asString())
                .isEqualTo("Cours payé par virement");

        // Same numeric user id, different tenant: the row must stay invisible.
        HttpResult otherTenant = get("/api/enrollments/me", token(tenantB, userId, Role.STUDENT));
        assertThat(json(otherTenant).size()).isZero();
        assertThat(otherTenant.body()).doesNotContain("Cours payé par virement");
        assertThat(TenantContext.callAs(tenantB,
                () -> enrollmentRepository.findById(enrollment.getId()))).isEmpty();
    }

    @Test
    void sameEventDeliveredTwiceCreatesOneEnrollmentAndOneProcessedEvent() throws Exception {
        String tenantId = newTenantId();
        long userId = 502;
        long paymentId = 77_002;
        Course course = saveCourse(tenantId, 100, "Cours idempotent", CourseStatus.PUBLISHED);
        String eventId = UUID.randomUUID().toString();
        String payload = approvedEvent(eventId, tenantId, paymentId, userId, course.getId());
        double duplicateBefore = counter("duplicate");

        send(paymentId, payload);
        send(paymentId, payload);

        await(Duration.ofSeconds(30), () -> counter("duplicate") >= duplicateBefore + 1);
        assertThat(awaitEnrollmentCount(tenantId, userId, course.getId(), 1)).isTrue();
        assertThat(processedEventCount(eventId)).isEqualTo(1);

        // The ledger is the first idempotency layer: once an event id is recorded, a redelivery
        // must do nothing at all, even if the enrollment row has disappeared in the meantime.
        // Without the processed_events check the consumer would silently recreate it.
        TenantContext.runAs(tenantId, () -> enrollmentRepository.deleteAll());
        double duplicateAfterFirstPass = counter("duplicate");

        send(paymentId, payload);

        await(Duration.ofSeconds(30),
                () -> counter("duplicate") >= duplicateAfterFirstPass + 1);
        assertThat(enrollmentCount(tenantId, userId, course.getId())).isZero();
        assertThat(processedEventCount(eventId)).isEqualTo(1);
    }

    @Test
    void twoEventsForTheSamePurchaseCreateOneEnrollment() throws Exception {
        String tenantId = newTenantId();
        long userId = 503;
        long paymentId = 77_003;
        Course course = saveCourse(tenantId, 100, "Cours double événement", CourseStatus.PUBLISHED);
        String firstEvent = UUID.randomUUID().toString();
        String secondEvent = UUID.randomUUID().toString();
        double duplicateBefore = counter("duplicate");

        send(paymentId, approvedEvent(firstEvent, tenantId, paymentId, userId, course.getId()));
        send(paymentId, approvedEvent(secondEvent, tenantId, paymentId, userId, course.getId()));

        await(Duration.ofSeconds(30), () -> counter("duplicate") >= duplicateBefore + 1);
        assertThat(awaitEnrollmentCount(tenantId, userId, course.getId(), 1)).isTrue();
        assertThat(processedEventCount(firstEvent)).isEqualTo(1);
        assertThat(processedEventCount(secondEvent)).isEqualTo(1);
    }

    @Test
    void submittedAndRejectedEventsCreateNothing() throws Exception {
        String tenantId = newTenantId();
        long userId = 504;
        long paymentId = 77_004;
        Course course = saveCourse(tenantId, 100, "Cours non approuvé", CourseStatus.PUBLISHED);
        String submittedId = UUID.randomUUID().toString();
        String rejectedId = UUID.randomUUID().toString();
        double ignoredBefore = counter("ignored");

        send(paymentId, event(submittedId, EventType.PAYMENT_SUBMITTED, tenantId, paymentId, userId,
                course.getId(), null));
        send(paymentId, event(rejectedId, EventType.PAYMENT_REJECTED, tenantId, paymentId, userId,
                course.getId(), "Justificatif illisible"));

        await(Duration.ofSeconds(30), () -> counter("ignored") >= ignoredBefore + 2);
        assertThat(enrollmentCount(tenantId, userId, course.getId())).isZero();
        assertThat(processedEventCount(submittedId)).isZero();
        assertThat(processedEventCount(rejectedId)).isZero();
    }

    @Test
    void enrollmentOfTenantAIsNeverVisibleToTenantB() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        long userId = 505;
        Course courseA = saveCourse(tenantA, 100, "Cours privé A", CourseStatus.PUBLISHED);
        String eventId = UUID.randomUUID().toString();

        send(77_005, approvedEvent(eventId, tenantA, 77_005, userId, courseA.getId()));
        Enrollment enrollment = awaitEnrollment(tenantA, userId, courseA.getId());

        HttpResult tenantBView = get("/api/enrollments/me", token(tenantB, userId, Role.STUDENT));
        assertThat(tenantBView.status()).isEqualTo(200);
        assertThat(json(tenantBView).size()).isZero();
        assertThat(TenantContext.callAs(tenantB,
                () -> enrollmentRepository.findById(enrollment.getId()))).isEmpty();
        assertThat(TenantContext.callAs(tenantB,
                () -> enrollmentRepository.findByUserIdAndCourseId(userId, courseA.getId())))
                .isEmpty();
    }

    // ---------------------------------------------------------------- poison records

    @Test
    void malformedJsonGoesToTheDeadLetterTopicWithoutCreatingAnything() throws Exception {
        String key = "poison-" + UUID.randomUUID();

        assertDeadLettered(key, "{ \"eventId\": ", null);
    }

    @Test
    void nilTenantGoesToTheDeadLetterTopic() throws Exception {
        String tenantId = TenantContext.NIL_TENANT;
        String eventId = UUID.randomUUID().toString();
        Course course = saveCourse(newTenantId(), 100, "Cours tenant nul", CourseStatus.PUBLISHED);

        assertDeadLettered(Long.toString(77_006),
                approvedEvent(eventId, tenantId, 77_006, 506, course.getId()), eventId);
    }

    @Test
    void invalidTenantUuidGoesToTheDeadLetterTopic() throws Exception {
        String eventId = UUID.randomUUID().toString();
        JsonNode envelope = JSON.readTree(
                approvedEvent(eventId, newTenantId(), 77_007, 507, 900_001));
        ((tools.jackson.databind.node.ObjectNode) envelope).put("tenantId", "not-a-uuid");

        assertDeadLettered(Long.toString(77_007), JSON.writeValueAsString(envelope), eventId);
    }

    @Test
    void unknownCourseGoesToTheDeadLetterTopic() throws Exception {
        String tenantId = newTenantId();
        String eventId = UUID.randomUUID().toString();

        assertDeadLettered(Long.toString(77_008),
                approvedEvent(eventId, tenantId, 77_008, 508, 999_999_999L), eventId);
    }

    @Test
    void unknownEventTypeGoesToTheDeadLetterTopic() throws Exception {
        String tenantId = newTenantId();
        String eventId = UUID.randomUUID().toString();
        Course course = saveCourse(tenantId, 100, "Cours type inconnu", CourseStatus.PUBLISHED);
        String payload = approvedEvent(eventId, tenantId, 77_009, 509, course.getId())
                .replace("PAYMENT_APPROVED", "PAYMENT_REFUNDED");

        assertDeadLettered(Long.toString(77_009), payload, eventId);
    }

    // ---------------------------------------------------------------- retries

    @Test
    void transientDatabaseFailureIsRetriedAndThenSucceeds() throws Exception {
        String tenantId = newTenantId();
        long userId = 510;
        long paymentId = 77_010;
        Course course = saveCourse(tenantId, 100, "Cours après incident", CourseStatus.PUBLISHED);
        String eventId = UUID.randomUUID().toString();

        doThrow(new TransientDataAccessResourceException("simulated transient failure"))
                .doCallRealMethod()
                .when(processedEventRepository).markProcessed(anyString());

        send(paymentId, approvedEvent(eventId, tenantId, paymentId, userId, course.getId()));

        Enrollment enrollment = awaitEnrollment(tenantId, userId, course.getId());
        assertThat(enrollment.getSourcePaymentId()).isEqualTo(paymentId);
        verify(processedEventRepository, times(2)).markProcessed(eventId);
        assertThat(processedEventCount(eventId)).isEqualTo(1);
        assertThat(enrollmentCount(tenantId, userId, course.getId())).isEqualTo(1);
    }

    // ---------------------------------------------------------------- startup and metrics

    @Test
    void startsAndServesHealthWhileTheBrokerIsPaused() throws Exception {
        pauseBroker();
        ConfigurableApplicationContext context = null;
        try {
            // Command-line arguments, not default properties: application.yml must not win.
            context = new SpringApplicationBuilder(CourseServiceApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run("--server.port=0",
                            "--management.server.port=0",
                            "--spring.datasource.url=" + MYSQL.getJdbcUrl()
                                    + "?useSSL=false&allowPublicKeyRetrieval=true",
                            "--spring.datasource.username=" + MYSQL.getUsername(),
                            "--spring.datasource.password=" + MYSQL.getPassword(),
                            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                            "--spring.kafka.admin.operation-timeout=1s",
                            "--sanaa.jwt.jwks-uri=http://127.0.0.1:1/.well-known/jwks.json");

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
    void prometheusExposesEventMetricsWithoutTenantTags() throws Exception {
        String tenantId = newTenantId();
        long userId = 511;
        long paymentId = 77_011;
        Course course = saveCourse(tenantId, 100, "Cours métriques", CourseStatus.PUBLISHED);
        String eventId = UUID.randomUUID().toString();
        double processedBefore = counter("processed");

        send(paymentId, approvedEvent(eventId, tenantId, paymentId, userId, course.getId()));
        await(Duration.ofSeconds(30), () -> counter("processed") >= processedBefore + 1);

        HttpResponse<String> metrics = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort
                        + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("sanaa_events_processed_total");
        assertThat(metrics.body()).contains("result=\"processed\"");
        assertThat(metrics.body()).contains("result=\"duplicate\"");
        assertThat(metrics.body()).contains("result=\"ignored\"");
        assertThat(metrics.body()).contains("result=\"dead_lettered\"");
        assertThat(metrics.body()).contains("application=\"course-service\"");
        assertThat(metrics.body()).doesNotContain(tenantId);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Sends a record that must end up on the DLT, then asserts it arrived once, quickly, without
     * touching the enrollment or the idempotency ledger.
     */
    private void assertDeadLettered(String key, String payload, String eventId) throws Exception {
        double deadLetteredBefore = counter("dead_lettered");
        long startedAt = System.nanoTime();

        send(key, payload);

        List<ConsumerRecord<String, String>> records = awaitDlt(key, 1, Duration.ofSeconds(15));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().value()).isEqualTo(payload);
        assertThat(records.getFirst().topic()).isEqualTo(DLT_TOPIC);
        // A non-retryable record must not go through the backoff cycle.
        assertThat(elapsedMs).isLessThan(5_000);
        assertThat(counter("dead_lettered")).isGreaterThanOrEqualTo(deadLetteredBefore + 1);
        if (eventId != null) {
            assertThat(processedEventCount(eventId)).isZero();
        }

        // No retry storm: a second pass over the DLT still finds exactly one copy.
        Thread.sleep(1_200);
        assertThat(awaitDlt(key, 2, Duration.ofSeconds(2))).hasSize(1);
    }

    private void send(long key, String payload) throws Exception {
        send(Long.toString(key), payload);
    }

    private void send(String key, String payload) throws Exception {
        kafkaTemplate.send(PAYMENTS_TOPIC, key, payload).get(10, TimeUnit.SECONDS);
    }

    private String approvedEvent(String eventId, String tenantId, long paymentId, long userId,
                                 long courseId) {
        return event(eventId, EventType.PAYMENT_APPROVED, tenantId, paymentId, userId, courseId,
                null);
    }

    private String event(String eventId, EventType type, String tenantId, long paymentId,
                         long userId, long courseId, String reason) {
        EventEnvelope<PaymentEventPayload> envelope = new EventEnvelope<>(
                eventId,
                type,
                Instant.now(),
                tenantId,
                new PaymentEventPayload(
                        paymentId,
                        userId,
                        "student" + userId + "@example.test",
                        "Étudiant " + userId,
                        courseId,
                        "Cours " + courseId,
                        "199.00",
                        "MAD",
                        reason));
        return EventJson.toJson(envelope);
    }

    private Enrollment awaitEnrollment(String tenantId, long userId, long courseId) {
        await(Duration.ofSeconds(30), () -> TenantContext.callAs(tenantId,
                () -> enrollmentRepository.findByUserIdAndCourseId(userId, courseId).isPresent()));
        return TenantContext.callAs(tenantId,
                () -> enrollmentRepository.findByUserIdAndCourseId(userId, courseId).orElseThrow());
    }

    private boolean awaitEnrollmentCount(String tenantId, long userId, long courseId, long expected) {
        await(Duration.ofSeconds(10), () -> enrollmentCount(tenantId, userId, courseId) == expected);
        return enrollmentCount(tenantId, userId, courseId) == expected;
    }

    private long enrollmentCount(String tenantId, long userId, long courseId) {
        return TenantContext.callAs(tenantId, () -> enrollmentRepository.findAll().stream()
                .filter(enrollment -> enrollment.getUserId() == userId
                        && enrollment.getCourseId() == courseId)
                .count());
    }

    private long processedEventCount(String eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, eventId);
        return count == null ? 0 : count;
    }

    private double counter(String result) {
        Counter counter = meterRegistry.find("sanaa.events.processed").tag("result", result)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private List<ConsumerRecord<String, String>> awaitDlt(String key, int expected,
                                                          Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
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

    private Course saveCourse(String tenantId, long instructorId, String title,
                              CourseStatus status) {
        Course course = new Course(title, "Description", new java.math.BigDecimal("199.00"),
                "MAD", instructorId);
        if (status == CourseStatus.PUBLISHED) {
            course.publish();
        }
        return TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
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
                            .claim("email", "student" + userId + "@example.test")
                            .claim("name", "Étudiant " + userId)
                            .claim("tenant_id", tenantId)
                            .build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResult get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path))
                .GET()
                .header("Authorization", "Bearer " + token);
        HttpResponse<String> response = HTTP.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private static JsonNode json(HttpResult result) {
        return JSON.readTree(result.body());
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

    private record HttpResult(int status, String body) {
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
