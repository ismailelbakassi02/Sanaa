package com.sanaa.payment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.common.event.EventEnvelope;
import com.sanaa.common.event.EventJson;
import com.sanaa.common.event.EventType;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.payment.domain.OutboxEvent;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentMethod;
import com.sanaa.payment.domain.PaymentStatus;
import com.sanaa.payment.repository.OutboxEventRepository;
import com.sanaa.payment.repository.PaymentRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end HTTP tests against a real MySQL instance. Tokens are signed with a generated RSA key
 * and validated by a test {@link JwtDecoder} that keeps the production issuer and audience rules.
 * course-service is replaced by a local stub that records the Authorization header it receives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(PaymentIntegrationTest.JwtTestConfiguration.class)
class PaymentIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final BigDecimal PRICE = new BigDecimal("299.00");
    private static final String ISSUER = "sanaa-auth";
    private static final String AUDIENCE = "sanaa-api";
    private static final String KEY_ID = "test-key";
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Path RECEIPTS = createReceiptsDirectory();
    private static final CourseStub COURSE = CourseStub.start();
    private static final AtomicLong COURSE_IDS = new AtomicLong(100_000);

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("payment_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // SSL is disabled only because the container certificate can be ahead of the host clock on
        // Windows, which makes the driver reject the handshake.
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("management.server.port", () -> "0");
        registry.add("sanaa.payment.course-service-url", COURSE::baseUrl);
        registry.add("sanaa.payment.receipts-dir", () -> RECEIPTS.toString());
        registry.add("sanaa.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
        // These tests assert the outbox rows before publication, so the poller must not run.
        registry.add("sanaa.outbox.poller.enabled", () -> "false");
        // No broker in this class; keep the topic admin from waiting on a default port.
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:1");
        registry.add("spring.kafka.admin.properties[default.api.timeout.ms]", () -> "1000");
        registry.add("spring.kafka.admin.properties[request.timeout.ms]", () -> "1000");
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetState() {
        TenantContext.clear();
        COURSE.reset();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- creation

    @Test
    void createPaymentUsesCourseDataAndStoresTheVerifiedReceipt() throws Exception {
        String tenantId = newTenantId();
        long userId = 101;
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours Java", PRICE, "PUBLISHED");
        String token = token(tenantId, userId, Role.STUDENT);

        Map<String, String> fields = paymentFields(courseId);
        // Extra client fields must have no effect: the amount and title come from course-service.
        fields.put("amount", "1.00");
        fields.put("title", "Cours pirate");

        HttpResult created = postMultipart("/api/payments", fields,
                "receipt", "proof.png", "image/png", PNG_BYTES, token);

        assertThat(created.status()).isEqualTo(201);
        JsonNode json = json(created);
        assertThat(json.get("userId").asLong()).isEqualTo(userId);
        assertThat(json.get("courseId").asLong()).isEqualTo(courseId);
        assertThat(json.get("courseTitle").asString()).isEqualTo("Cours Java");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo(PRICE);
        assertThat(json.get("currency").asString()).isEqualTo("MAD");
        assertThat(json.get("status").asString()).isEqualTo("PENDING_REVIEW");
        assertThat(json.get("method").asString()).isEqualTo("BANK_WIRE");
        assertThat(json.get("receiptContentType").asString()).isEqualTo("image/png");
        assertThat(json.get("userEmail").asString()).isEqualTo("user" + userId + "@example.test");

        // The stub saw the caller's own bearer token, not a service credential.
        assertThat(COURSE.lastAuthorization()).isEqualTo("Bearer " + token);

        long paymentId = json.get("id").asLong();
        Payment payment = TenantContext.callAs(tenantId,
                () -> paymentRepository.findById(paymentId).orElseThrow());
        String relativePath = payment.getReceiptPath();
        assertThat(relativePath).startsWith(tenantId + "/");
        String storedName = relativePath.substring((tenantId + "/").length());
        assertThat(storedName).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(storedName).doesNotContain(".");
        assertThat(Files.readAllBytes(RECEIPTS.resolve(relativePath))).isEqualTo(PNG_BYTES);

        List<OutboxEvent> events = outboxEventRepository
                .findAllByAggregateIdOrderByIdAsc(paymentId);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventType()).isEqualTo(EventType.PAYMENT_SUBMITTED);
        assertThat(events.getFirst().getTopic()).isEqualTo("sanaa.payments.v1");
        assertThat(events.getFirst().getPublishedAt()).isNull();
        EventEnvelope envelope = EventJson.fromJson(events.getFirst().getPayload(),
                EventEnvelope.class);
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.eventType()).isEqualTo(EventType.PAYMENT_SUBMITTED);
        assertThat(envelope.payload().paymentId()).isEqualTo(paymentId);
        assertThat(envelope.payload().amount()).isEqualTo("299.00");
        assertThat(envelope.payload().reason()).isNull();
    }

    @Test
    void creationRejectsUnknownUnpublishedAndFreeCourses() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 102, Role.STUDENT);

        HttpResult unknown = createPayment(token, nextCourseId());
        assertThat(unknown.status()).isEqualTo(404);

        long draftCourseId = nextCourseId();
        COURSE.course(draftCourseId, "Brouillon", PRICE, "DRAFT");
        HttpResult draft = createPayment(token, draftCourseId);
        assertThat(draft.status()).isEqualTo(404);

        long freeCourseId = nextCourseId();
        COURSE.course(freeCourseId, "Cours gratuit", BigDecimal.ZERO, "PUBLISHED");
        HttpResult free = createPayment(token, freeCourseId);
        assertThat(free.status()).isEqualTo(422);
        assertThat(json(free).get("detail").asString())
                .contains("Free courses do not need a payment");
    }

    @Test
    void creationRejectsAStudentWhoIsAlreadyEnrolled() throws Exception {
        String tenantId = newTenantId();
        long userId = 103;
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours déjà suivi", PRICE, "PUBLISHED");
        COURSE.enroll(userId, courseId);

        HttpResult result = createPayment(token(tenantId, userId, Role.STUDENT), courseId);

        assertThat(result.status()).isEqualTo(409);
        assertThat(receiptFileCount(tenantId)).isZero();
    }

    @Test
    void rejectedPaymentCanBeSubmittedAgainButAnApprovedOneCannot() throws Exception {
        String tenantId = newTenantId();
        long userId = 130;
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours reprise", PRICE, "PUBLISHED");
        String studentToken = token(tenantId, userId, Role.STUDENT);
        String adminToken = token(tenantId, 910, Role.TENANT_ADMIN);

        long firstId = json(createPayment(studentToken, courseId)).get("id").asLong();
        assertThat(postJson("/api/payments/" + firstId + "/reject",
                Map.of("reason", "Justificatif illisible"), adminToken).status()).isEqualTo(200);

        // The generated active key is NULL for a rejected row, so the unique index frees the slot.
        HttpResult resubmitted = createPayment(studentToken, courseId);
        assertThat(resubmitted.status()).isEqualTo(201);
        long secondId = json(resubmitted).get("id").asLong();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(json(resubmitted).get("status").asString()).isEqualTo("PENDING_REVIEW");

        assertThat(post("/api/payments/" + secondId + "/approve", adminToken).status())
                .isEqualTo(200);

        // An approved payment keeps the slot: a second open payment would double charge the course.
        HttpResult blocked = createPayment(studentToken, courseId);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(receiptFileCount(tenantId)).isEqualTo(2);
    }

    @Test
    void creationReturns503WhenCourseServiceIsSlow() throws Exception {
        String tenantId = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours lent", PRICE, "PUBLISHED");
        COURSE.mode(StubMode.DELAY);

        HttpResult result = createPayment(token(tenantId, 104, Role.STUDENT), courseId);

        assertThat(result.status()).isEqualTo(503);
        assertThat(json(result).get("detail").asString())
                .contains("temporarily unavailable");
    }

    @Test
    void creationReturns503WhenCourseServiceIsDead() throws Exception {
        String tenantId = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours injoignable", PRICE, "PUBLISHED");
        COURSE.mode(StubMode.DROP);

        HttpResult result = createPayment(token(tenantId, 105, Role.STUDENT), courseId);

        assertThat(result.status()).isEqualTo(503);
    }

    // ---------------------------------------------------------------- receipts

    @Test
    void receiptValidationUsesMagicBytesNotTheDeclaredType() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 106, Role.STUDENT);

        long textCourse = nextCourseId();
        COURSE.course(textCourse, "Cours texte", PRICE, "PUBLISHED");
        HttpResult fakePdf = postMultipart("/api/payments", paymentFields(textCourse),
                "receipt", "receipt.pdf", "application/pdf",
                "This is definitely not a PDF".getBytes(StandardCharsets.UTF_8), token);
        assertThat(fakePdf.status()).isEqualTo(422);

        long emptyCourse = nextCourseId();
        COURSE.course(emptyCourse, "Cours vide", PRICE, "PUBLISHED");
        HttpResult empty = postMultipart("/api/payments", paymentFields(emptyCourse),
                "receipt", "empty.png", "image/png", new byte[0], token);
        assertThat(empty.status()).isEqualTo(422);

        long largeCourse = nextCourseId();
        COURSE.course(largeCourse, "Cours lourd", PRICE, "PUBLISHED");
        byte[] oversized = new byte[(int) MAX_FILE_BYTES + 1];
        System.arraycopy(PNG_BYTES, 0, oversized, 0, PNG_BYTES.length);
        HttpResult tooLarge = postMultipart("/api/payments", paymentFields(largeCourse),
                "receipt", "large.png", "image/png", oversized, token);
        assertThat(tooLarge.status()).isEqualTo(413);

        long pngCourse = nextCourseId();
        COURSE.course(pngCourse, "Cours image", PRICE, "PUBLISHED");
        HttpResult mislabeled = postMultipart("/api/payments", paymentFields(pngCourse),
                "receipt", "notes.txt", "text/plain", PNG_BYTES, token);
        assertThat(mislabeled.status()).isEqualTo(201);
        assertThat(json(mislabeled).get("receiptContentType").asString()).isEqualTo("image/png");
    }

    @Test
    void failedInsertDeletesTheStoredReceipt() throws Exception {
        String tenantId = newTenantId();
        long userId = 107;
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours doublon", PRICE, "PUBLISHED");
        String token = token(tenantId, userId, Role.STUDENT);

        assertThat(createPayment(token, courseId).status()).isEqualTo(201);
        assertThat(receiptFileCount(tenantId)).isEqualTo(1);

        // The unique active-key index rejects the second insert after the file was written.
        HttpResult duplicate = createPayment(token, courseId);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(receiptFileCount(tenantId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- listings

    @Test
    void studentListsOnlyOwnPaymentsNewestFirstWithCappedSize() throws Exception {
        String tenantId = newTenantId();
        long userId = 108;
        String token = token(tenantId, userId, Role.STUDENT);

        List<Long> createdIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long courseId = nextCourseId();
            COURSE.course(courseId, "Cours " + i, new BigDecimal("100.00"), "PUBLISHED");
            HttpResult created = createPayment(token, courseId);
            assertThat(created.status()).isEqualTo(201);
            createdIds.add(json(created).get("id").asLong());
        }

        long otherCourse = nextCourseId();
        COURSE.course(otherCourse, "Cours autre", PRICE, "PUBLISHED");
        assertThat(createPayment(token(tenantId, 109, Role.STUDENT), otherCourse).status())
                .isEqualTo(201);

        HttpResult mine = get("/api/payments/me?page=0&size=100", token);
        assertThat(mine.status()).isEqualTo(200);
        JsonNode page = json(mine);
        assertThat(page.get("size").asInt()).isEqualTo(50);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        JsonNode items = page.get("items");
        assertThat(items.size()).isEqualTo(3);
        assertThat(items.get(0).get("id").asLong())
                .isEqualTo(createdIds.get(2));
        assertThat(items.get(2).get("id").asLong())
                .isEqualTo(createdIds.get(0));
        assertThat(mine.text()).doesNotContain(String.valueOf(otherCourse));
    }

    @Test
    void adminListsTenantPaymentsAndFiltersByStatus() throws Exception {
        String tenantId = newTenantId();
        String adminToken = token(tenantId, 500, Role.TENANT_ADMIN);
        String studentToken = token(tenantId, 110, Role.STUDENT);

        long firstCourse = nextCourseId();
        COURSE.course(firstCourse, "Cours un", PRICE, "PUBLISHED");
        long firstId = json(createPayment(studentToken, firstCourse)).get("id").asLong();

        long secondCourse = nextCourseId();
        COURSE.course(secondCourse, "Cours deux", PRICE, "PUBLISHED");
        long secondId = json(createPayment(studentToken, secondCourse)).get("id").asLong();

        assertThat(post("/api/payments/" + firstId + "/approve", adminToken).status())
                .isEqualTo(200);

        HttpResult all = get("/api/payments?size=50", adminToken);
        assertThat(all.status()).isEqualTo(200);
        assertThat(json(all).get("totalElements").asLong()).isEqualTo(2);

        HttpResult pending = get("/api/payments?status=PENDING_REVIEW&size=50", adminToken);
        assertThat(pending.status()).isEqualTo(200);
        assertThat(json(pending).get("totalElements").asLong()).isEqualTo(1);
        assertThat(json(pending).get("items").get(0).get("id").asLong()).isEqualTo(secondId);

        HttpResult invalid = get("/api/payments?status=NOT_A_STATUS", adminToken);
        assertThat(invalid.status()).isEqualTo(400);
    }

    // ---------------------------------------------------------------- download

    @Test
    void receiptDownloadIsRestrictedToOwnerOrTenantAdmin() throws Exception {
        String tenantId = newTenantId();
        long ownerId = 111;
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours reçu", PRICE, "PUBLISHED");
        HttpResult created = createPayment(token(tenantId, ownerId, Role.STUDENT), courseId);
        long paymentId = json(created).get("id").asLong();

        HttpResult owner = get("/api/payments/" + paymentId + "/receipt",
                token(tenantId, ownerId, Role.STUDENT));
        assertThat(owner.status()).isEqualTo(200);
        assertThat(owner.body()).isEqualTo(PNG_BYTES);
        assertThat(owner.header("Content-Type")).isEqualTo("image/png");
        assertThat(owner.header("Content-Disposition")).startsWith("attachment");
        assertThat(owner.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(owner.header("Cache-Control")).isEqualTo("no-store");

        HttpResult otherStudent = get("/api/payments/" + paymentId + "/receipt",
                token(tenantId, 112, Role.STUDENT));
        assertThat(otherStudent.status()).isEqualTo(404);

        HttpResult sameTenantAdmin = get("/api/payments/" + paymentId + "/receipt",
                token(tenantId, 501, Role.TENANT_ADMIN));
        assertThat(sameTenantAdmin.status()).isEqualTo(200);

        HttpResult otherTenantAdmin = get("/api/payments/" + paymentId + "/receipt",
                token(newTenantId(), 502, Role.TENANT_ADMIN));
        assertThat(otherTenantAdmin.status()).isEqualTo(404);
    }

    // ---------------------------------------------------------------- isolation

    @Test
    void tenantIsolationHidesOtherTenantListings() throws Exception {
        String tenantA = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours Atlas", PRICE, "PUBLISHED");
        assertThat(createPayment(token(tenantA, 113, Role.STUDENT), courseId).status())
                .isEqualTo(201);

        HttpResult list = get("/api/payments?size=50",
                token(newTenantId(), 600, Role.TENANT_ADMIN));

        assertThat(list.status()).isEqualTo(200);
        assertThat(json(list).get("items").size()).isZero();
        assertThat(json(list).get("totalElements").asLong()).isZero();
    }

    @Test
    void tenantIsolationHidesOtherTenantReceipts() throws Exception {
        String tenantA = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours Atlas", PRICE, "PUBLISHED");
        long paymentId = json(createPayment(token(tenantA, 113, Role.STUDENT), courseId))
                .get("id").asLong();

        HttpResult receipt = get("/api/payments/" + paymentId + "/receipt",
                token(newTenantId(), 600, Role.TENANT_ADMIN));

        assertThat(receipt.status()).isEqualTo(404);
    }

    @Test
    void tenantIsolationRejectsOtherTenantApproval() throws Exception {
        String tenantA = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours Atlas", PRICE, "PUBLISHED");
        long paymentId = json(createPayment(token(tenantA, 113, Role.STUDENT), courseId))
                .get("id").asLong();

        HttpResult approve = post("/api/payments/" + paymentId + "/approve",
                token(newTenantId(), 600, Role.TENANT_ADMIN));

        assertThat(approve.status()).isEqualTo(404);
        Payment stored = TenantContext.callAs(tenantA,
                () -> paymentRepository.findById(paymentId).orElseThrow());
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING_REVIEW);
        assertThat(stored.getReviewedBy()).isNull();
    }

    @Test
    void tenantIsolationRejectsOtherTenantRejection() throws Exception {
        String tenantA = newTenantId();
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours Atlas", PRICE, "PUBLISHED");
        long paymentId = json(createPayment(token(tenantA, 113, Role.STUDENT), courseId))
                .get("id").asLong();

        HttpResult reject = postJson("/api/payments/" + paymentId + "/reject",
                Map.of("reason", "Justificatif illisible"),
                token(newTenantId(), 600, Role.TENANT_ADMIN));

        assertThat(reject.status()).isEqualTo(404);
        Payment stored = TenantContext.callAs(tenantA,
                () -> paymentRepository.findById(paymentId).orElseThrow());
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING_REVIEW);
        assertThat(stored.getRejectionReason()).isNull();
    }

    @Test
    void insertWithoutTenantContextFails() {
        Payment payment = new Payment(114, "sans-tenant@example.test", "Sans Tenant", 1,
                "Cours", new BigDecimal("10.00"), "MAD", PaymentMethod.CASHPLUS,
                "00000000-0000-0000-0000-000000000000/file", "image/png");

        TenantContext.clear();
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- roles

    @Test
    void instructorAndSuperAdminAreForbiddenEverywhere() throws Exception {
        for (Role role : List.of(Role.INSTRUCTOR, Role.SUPER_ADMIN)) {
            String tenantId = newTenantId();
            String token = token(tenantId, 700, role);
            long courseId = nextCourseId();
            COURSE.course(courseId, "Cours rôle", PRICE, "PUBLISHED");

            assertThat(postMultipart("/api/payments", paymentFields(courseId),
                    "receipt", "proof.png", "image/png", PNG_BYTES, token).status())
                    .isEqualTo(403);
            assertThat(get("/api/payments/me", token).status()).isEqualTo(403);
            assertThat(get("/api/payments?size=20", token).status()).isEqualTo(403);
            assertThat(get("/api/payments/1/receipt", token).status()).isEqualTo(403);
            assertThat(post("/api/payments/1/approve", token).status()).isEqualTo(403);
            assertThat(postJson("/api/payments/1/reject", Map.of("reason", "Refus"), token)
                    .status()).isEqualTo(403);
        }
    }

    @Test
    void studentCannotListAllOrReviewPayments() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 115, Role.STUDENT);

        assertThat(get("/api/payments?size=20", token).status()).isEqualTo(403);
        assertThat(post("/api/payments/1/approve", token).status()).isEqualTo(403);
        assertThat(postJson("/api/payments/1/reject", Map.of("reason", "Refus"), token)
                .status()).isEqualTo(403);
    }

    @Test
    void requestsWithoutATokenAreUnauthorized() throws Exception {
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours sans jeton", PRICE, "PUBLISHED");

        assertThat(get("/api/payments/me", null).status()).isEqualTo(401);
        assertThat(postMultipart("/api/payments", paymentFields(courseId),
                "receipt", "proof.png", "image/png", PNG_BYTES, null).status())
                .isEqualTo(401);
        assertThat(get("/api/payments?size=20", null).status()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- workflow

    @Test
    void approvingTwiceLeavesExactlyOneOutboxRow() throws Exception {
        String tenantId = newTenantId();
        String adminToken = token(tenantId, 800, Role.TENANT_ADMIN);
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours workflow", PRICE, "PUBLISHED");
        long paymentId = json(createPayment(token(tenantId, 116, Role.STUDENT), courseId))
                .get("id").asLong();

        HttpResult approved = post("/api/payments/" + paymentId + "/approve", adminToken);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(json(approved).get("status").asString()).isEqualTo("APPROVED");
        assertThat(json(approved).get("reviewedBy").asLong()).isEqualTo(800);

        HttpResult again = post("/api/payments/" + paymentId + "/approve", adminToken);
        assertThat(again.status()).isEqualTo(409);

        List<OutboxEvent> events = outboxEventRepository
                .findAllByAggregateIdOrderByIdAsc(paymentId);
        assertThat(events).hasSize(2);
        assertThat(events.stream().filter(event ->
                event.getEventType() == EventType.PAYMENT_APPROVED).count()).isEqualTo(1);
    }

    @Test
    void rejectionNeedsAReasonAndBlocksLaterApproval() throws Exception {
        String tenantId = newTenantId();
        String adminToken = token(tenantId, 801, Role.TENANT_ADMIN);
        long courseId = nextCourseId();
        COURSE.course(courseId, "Cours refus", PRICE, "PUBLISHED");
        long paymentId = json(createPayment(token(tenantId, 117, Role.STUDENT), courseId))
                .get("id").asLong();

        assertThat(postJson("/api/payments/" + paymentId + "/reject",
                Map.of("reason", ""), adminToken).status()).isEqualTo(400);
        assertThat(postJson("/api/payments/" + paymentId + "/reject",
                Map.of("reason", "ab"), adminToken).status()).isEqualTo(400);

        HttpResult rejected = postJson("/api/payments/" + paymentId + "/reject",
                Map.of("reason", "Justificatif illisible"), adminToken);
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(json(rejected).get("status").asString()).isEqualTo("REJECTED");
        assertThat(json(rejected).get("rejectionReason").asString())
                .isEqualTo("Justificatif illisible");

        OutboxEvent rejectedEvent = outboxEventRepository
                .findAllByAggregateIdOrderByIdAsc(paymentId).stream()
                .filter(event -> event.getEventType() == EventType.PAYMENT_REJECTED)
                .findFirst()
                .orElseThrow();
        EventEnvelope envelope = EventJson.fromJson(rejectedEvent.getPayload(),
                EventEnvelope.class);
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.payload().reason()).isEqualTo("Justificatif illisible");

        assertThat(post("/api/payments/" + paymentId + "/approve", adminToken).status())
                .isEqualTo(409);
    }

    @Test
    void parallelApprovalsProduceOneSuccessAndOneConflict() throws Exception {
        String tenantId = newTenantId();
        String adminToken = token(tenantId, 802, Role.TENANT_ADMIN);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 20; iteration++) {
                long courseId = nextCourseId();
                COURSE.course(courseId, "Cours parallèle " + iteration, PRICE, "PUBLISHED");
                long paymentId = json(createPayment(
                        token(tenantId, 118, Role.STUDENT), courseId)).get("id").asLong();

                List<Integer> statuses = runInParallel(pool,
                        () -> post("/api/payments/" + paymentId + "/approve", adminToken),
                        () -> post("/api/payments/" + paymentId + "/approve", adminToken));

                assertThat(statuses).containsExactlyInAnyOrder(200, 409);
                assertThat(outboxEventRepository
                        .findAllByAggregateIdOrderByIdAsc(paymentId).stream()
                        .filter(event -> event.getEventType() == EventType.PAYMENT_APPROVED)
                        .count()).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelCreationsProduceOnePaymentAndNoOrphanFile() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 119, Role.STUDENT);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 20; iteration++) {
                long courseId = nextCourseId();
                COURSE.course(courseId, "Cours création " + iteration, PRICE, "PUBLISHED");

                List<Integer> statuses = runInParallel(pool,
                        () -> createPayment(token, courseId),
                        () -> createPayment(token, courseId));

                assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            }
        } finally {
            pool.shutdownNow();
        }
        // Every winning submission keeps exactly one file; every rejected insert removed its own.
        assertThat(receiptFileCount(tenantId)).isEqualTo(20);
    }

    // ---------------------------------------------------------------- metrics

    @Test
    void prometheusIsOnlyExposedOnTheManagementPortWithTheApplicationTag() throws Exception {
        HttpResponse<String> metrics = HTTP.send(
                HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + managementPort + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("application=\"payment-service\"");

        HttpResult mainPort = get("/actuator/prometheus", null);
        assertThat(mainPort.status()).isNotEqualTo(200);
    }

    // ---------------------------------------------------------------- helpers

    private HttpResult createPayment(String token, long courseId) throws Exception {
        return postMultipart("/api/payments", paymentFields(courseId),
                "receipt", "proof.png", "image/png", PNG_BYTES, token);
    }

    private static Map<String, String> paymentFields(long courseId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("courseId", Long.toString(courseId));
        fields.put("method", "BANK_WIRE");
        return fields;
    }

    private long receiptFileCount(String tenantId) throws IOException {
        Path directory = RECEIPTS.resolve(tenantId);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        }
    }

    private static List<Integer> runInParallel(ExecutorService pool,
                                               Callable<HttpResult> first,
                                               Callable<HttpResult> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<HttpResult> firstResult = pool.submit(() -> {
            start.await();
            return first.call();
        });
        Future<HttpResult> secondResult = pool.submit(() -> {
            start.await();
            return second.call();
        });
        start.countDown();
        return List.of(firstResult.get(30, TimeUnit.SECONDS).status(),
                secondResult.get(30, TimeUnit.SECONDS).status());
    }

    private String token(String tenantId, long userId, Role role) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .subject(Long.toString(userId))
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", role.name())
                    .claim("email", "user" + userId + "@example.test")
                    .claim("name", "User " + userId);
            if (tenantId != null) {
                claims.claim("tenant_id", tenantId);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResult get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult post(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult postJson(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult postMultipart(String path, Map<String, String> fields, String fileField,
                                     String filename, String declaredType, byte[] content,
                                     String token) throws Exception {
        String boundary = "----sanaa" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.write(field.getValue().getBytes(StandardCharsets.UTF_8));
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + fileField
                + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + declaredType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new HttpResult(response.statusCode(), response.body(), response.headers());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static JsonNode json(HttpResult result) {
        return JSON.readTree(result.text());
    }

    private static String newTenantId() {
        return UUID.randomUUID().toString();
    }

    private static long nextCourseId() {
        return COURSE_IDS.incrementAndGet();
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
            return Files.createTempDirectory("sanaa-payment-receipts");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record HttpResult(int status, byte[] body, HttpHeaders headers) {

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.firstValue(name).orElse("");
        }
    }

    // ---------------------------------------------------------------- course stub

    private enum StubMode {
        NORMAL,
        DELAY,
        DROP
    }

    private record StubCourse(String title, BigDecimal price, String status) {
    }

    /**
     * Minimal course-service stand-in. It records the Authorization header and can delay or drop
     * replies to exercise the 503 paths.
     */
    private static final class CourseStub {

        private final HttpServer server;
        private final Map<Long, StubCourse> courses = new ConcurrentHashMap<>();
        private final Map<Long, Set<Long>> enrollments = new ConcurrentHashMap<>();
        private volatile StubMode mode = StubMode.NORMAL;
        private volatile String lastAuthorization;

        private CourseStub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/courses/", this::handleCourse);
            server.createContext("/api/enrollments/me", this::handleEnrollments);
            server.start();
        }

        static CourseStub start() {
            try {
                return new CourseStub();
            } catch (IOException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String lastAuthorization() {
            return lastAuthorization;
        }

        void course(long id, String title, BigDecimal price, String status) {
            courses.put(id, new StubCourse(title, price, status));
        }

        void enroll(long userId, long courseId) {
            enrollments.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                    .add(courseId);
        }

        void mode(StubMode mode) {
            this.mode = mode;
        }

        void reset() {
            courses.clear();
            enrollments.clear();
            mode = StubMode.NORMAL;
            lastAuthorization = null;
        }

        private void handleCourse(HttpExchange exchange) throws IOException {
            try {
                lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (pauseOrDrop(exchange)) {
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                long id = Long.parseLong(path.substring("/api/courses/".length()));
                StubCourse course = courses.get(id);
                if (course == null) {
                    respond(exchange, 404, "{\"detail\":\"Course not found.\"}");
                    return;
                }
                Map<String, Object> body = new HashMap<>();
                body.put("id", id);
                body.put("title", course.title());
                body.put("price", course.price());
                body.put("currency", "MAD");
                body.put("status", course.status());
                respond(exchange, 200, JSON.writeValueAsString(body));
            } catch (RuntimeException exception) {
                respond(exchange, 500, "{\"detail\":\"stub failure\"}");
            }
        }

        private void handleEnrollments(HttpExchange exchange) throws IOException {
            try {
                lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (pauseOrDrop(exchange)) {
                    return;
                }
                long userId = subjectOf(lastAuthorization);
                List<Map<String, Object>> body = new ArrayList<>();
                for (Long courseId : enrollments.getOrDefault(userId, Set.of())) {
                    Map<String, Object> course = new HashMap<>();
                    course.put("id", courseId);
                    Map<String, Object> enrollment = new HashMap<>();
                    enrollment.put("id", courseId);
                    enrollment.put("status", "ACTIVE");
                    enrollment.put("enrolledAt", "2026-11-10T00:00:00Z");
                    enrollment.put("course", course);
                    body.add(enrollment);
                }
                respond(exchange, 200, JSON.writeValueAsString(body));
            } catch (RuntimeException exception) {
                respond(exchange, 500, "{\"detail\":\"stub failure\"}");
            }
        }

        /**
         * @return true when the exchange was already closed or delayed
         */
        private boolean pauseOrDrop(HttpExchange exchange) {
            if (mode == StubMode.DELAY) {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            if (mode == StubMode.DROP) {
                exchange.close();
                return true;
            }
            return false;
        }

        private static long subjectOf(String authorization) {
            try {
                String raw = authorization.substring("Bearer ".length());
                return Long.parseLong(SignedJWT.parse(raw).getJWTClaimsSet().getSubject());
            } catch (Exception exception) {
                return -1;
            }
        }

        private static void respond(HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
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
