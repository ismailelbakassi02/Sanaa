package com.sanaa.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.gateway.support.StubUpstream;
import com.sanaa.gateway.support.TestTokens;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the gateway: real WebFlux server, real Redis (Testcontainers), stub
 * upstreams and a stub JWKS endpoint.
 *
 * <p>The v2 routes are split by owner: auth-service owns identity, publishers and admin identity
 * routes, course-service owns catalogue, studio and learner routes, payment-service owns money,
 * and the support routes go to port 8085. The tests assert each prefix reaches the right stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(REDIS_PORT);

    private static final TestTokens TOKENS = TestTokens.generate();
    private static final StubUpstream AUTH;
    private static final StubUpstream COURSE;
    private static final StubUpstream PAYMENT;
    private static final StubUpstream SUPPORT;
    private static final StubUpstream JWKS;

    static {
        REDIS.start();
        AUTH = StubUpstream.service("auth");
        COURSE = StubUpstream.service("course");
        PAYMENT = StubUpstream.service("payment");
        SUPPORT = StubUpstream.service("support");
        JWKS = StubUpstream.json(TOKENS.jwksJson());
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("AUTH_SERVICE_URI", AUTH::uri);
        registry.add("COURSE_SERVICE_URI", COURSE::uri);
        registry.add("PAYMENT_SERVICE_URI", PAYMENT::uri);
        registry.add("SUPPORT_SERVICE_URI", SUPPORT::uri);
        registry.add("AUTH_JWKS_URI", () -> JWKS.uri() + "/jwks.json");
        // A random management port lets the test assert that 9090 is not the application port.
        registry.add("management.server.port", () -> 0);
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    private WebTestClient client;

    @AfterAll
    static void stopStubs() {
        AUTH.close();
        COURSE.close();
        PAYMENT.close();
        SUPPORT.close();
        JWKS.close();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        AUTH.clear();
        COURSE.clear();
        PAYMENT.clear();
        SUPPORT.clear();
    }

    // ---------------------------------------------------------------- startup

    @Test
    @Order(1)
    void startsWithoutContactingAuthService() {
        // The context is up while auth-service does not exist. The JWKS document is fetched on the
        // first token, not at startup, so the stub has not been called yet.
        assertThat(JWKS.requestCount()).isZero();
        assertThat(AUTH.requestCount()).isZero();
        assertThat(managementPort).isNotEqualTo(port);
    }

    // ------------------------------------------------------------ 401 handling

    @Test
    void rejectsMissingToken() {
        client.get().uri("/api/courses/1").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"status\":401")
                        .contains("A valid access token is required.")
                        .doesNotContain("\"properties\":null", "\"type\":null"));
    }

    @Test
    void rejectsMalformedToken() {
        after("/api/courses/1", "Bearer this-is-not-a-jwt")
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"invalid_token\"")
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"status\":401"));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = TOKENS.token(claims -> claims
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120))));
        after("/api/courses/1", "Bearer " + expired)
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsWrongAudience() {
        String wrongAudience = TOKENS.token(claims ->
                claims.audience(JwtClaims.AUDIENCE_PLAYBACK));
        after("/api/courses/1", "Bearer " + wrongAudience)
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsWrongIssuer() {
        String wrongIssuer = TOKENS.token(claims -> claims.issuer("another-issuer"));
        after("/api/courses/1", "Bearer " + wrongIssuer)
                .expectStatus().isUnauthorized();
    }

    // ---------------------------------------------------------------- routing

    @Test
    void routesAuthIdentityAndPublisherPathsToAuthService() {
        String token = TOKENS.publisherToken("user-auth", "TENANT_ADMIN");
        for (String path : List.of(
                "/api/auth/me",
                "/api/auth/profile",
                "/api/auth/team",
                "/api/auth/publishers/me")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        }
        assertThat(PAYMENT.requestCount()).isZero();
    }

    @Test
    void routesCoursePathsToCourseService() {
        String token = TOKENS.learnerToken("user-course");
        for (String path : List.of(
                "/api/courses/1",
                "/api/modules/1",
                "/api/lessons/1",
                "/api/enrollments/1",
                "/api/stream/1/status")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"course\""));
        }
    }

    @Test
    void routesPaymentsToPaymentService() {
        get("/api/payments/invoices", TOKENS.learnerToken("user-payment"))
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"payment\""));
        assertThat(PAYMENT.requestCount()).isEqualTo(1);
    }

    @Test
    void routesLearnerAndStudioPathsToTheOwningService() {
        String token = TOKENS.publisherToken("user-studio", "INSTRUCTOR");

        get("/api/learner/courses", TOKENS.learnerToken("user-learner"))
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"course\""));

        for (String path : List.of(
                "/api/studio/courses/1",
                "/api/studio/lessons/1",
                "/api/studio/reviews",
                "/api/studio/stats")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"course\""));
        }
        for (String path : List.of(
                "/api/studio/payments/1",
                "/api/studio/payment-settings")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"payment\""));
        }
    }

    @Test
    void routesAdminPathsToTheServiceThatOwnsTheResource() {
        String token = TOKENS.token(claims -> claims
                .subject("super-admin")
                .claim(JwtClaims.ROLE, "SUPER_ADMIN"));

        for (String path : List.of(
                "/api/admin/users",
                "/api/admin/publishers",
                "/api/admin/audit/auth")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        }
        for (String path : List.of(
                "/api/admin/courses/1",
                "/api/admin/reviews/1",
                "/api/admin/stats",
                "/api/admin/audit/course")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"course\""));
        }
        for (String path : List.of(
                "/api/admin/payments/1",
                "/api/admin/audit/payment")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"payment\""));
        }
    }

    @Test
    void routesComplaintPathsToTheSupportService() {
        String token = TOKENS.learnerToken("user-support");
        for (String path : List.of(
                "/api/complaints",
                "/api/studio/complaints",
                "/api/admin/complaints",
                "/api/admin/audit/support")) {
            get(path, token).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"support\""));
        }
        assertThat(AUTH.requestCount()).isZero();
        assertThat(COURSE.requestCount()).isZero();
    }

    @Test
    void routesPublicPublisherInstructorAndImagePagesToAuthService() {
        for (String path : List.of(
                "/api/public/publishers/atlas",
                "/api/public/instructors/1",
                "/api/public/images/11111111-1111-1111-1111-111111111111")) {
            get(path, null).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        }
        get("/api/public/courses", null).expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"course\""));
    }

    @Test
    void doesNotRouteNotificationsOrActuator() {
        get("/api/notifications/emails", TOKENS.learnerToken("user-404"))
                .expectStatus().isNotFound();
        client.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
        assertThat(AUTH.requestCount()).isZero();
        assertThat(COURSE.requestCount()).isZero();
        assertThat(PAYMENT.requestCount()).isZero();
    }

    // ------------------------------------------------------------ public routes

    @Test
    void allowsPublicRoutesWithoutToken() {
        for (String path : List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/refresh",
                "/api/auth/logout")) {
            client.post().uri(path)
                    .header(X_FORWARDED_FOR, nextIp())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        }

        // Publisher creation has its own stricter policy, and the slug check belongs to policy A.
        client.post().uri("/api/auth/publishers")
                .header(X_FORWARDED_FOR, nextIp())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        get("/api/auth/publishers/slug-available?slug=atlas", null).expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"auth\""));

        for (String path : List.of(
                "/api/public/publishers/atlas",
                "/api/public/instructors/1",
                "/api/public/images/1")) {
            get(path, null).expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        }
        get("/api/public/courses", null).expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"course\""));

        get("/api/stream/media/lesson-1/index.m3u8", null).expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"course\""));

        // The card provider callback is public and authenticated by its signature, not a token.
        client.post().uri("/api/payments/card/callback")
                .header(X_FORWARDED_FOR, nextIp())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"payment\""));
    }

    // -------------------------------------------------------------- rate limits

    @Test
    void policyALimitsLoginBurstPerClientIp() {
        String burstIp = nextIp();
        List<Integer> statuses = new ArrayList<>();
        String retryAfter = null;

        for (int i = 0; i < 20; i++) {
            EntityExchangeResult<String> result = client.post().uri("/api/auth/login")
                    .header(X_FORWARDED_FOR, burstIp)
                    .exchange()
                    .expectBody(String.class)
                    .returnResult();
            int status = result.getStatus().value();
            statuses.add(status);
            if (status == 429) {
                retryAfter = result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            }
        }

        assertThat(statuses.getFirst()).isEqualTo(200);
        assertThat(statuses).contains(429);
        // The exact number of allowed requests depends on how long the loop takes, because the
        // bucket refills at 2 tokens per second while it runs. The invariant is that the burst is
        // capped and at least one request is rejected.
        assertThat(statuses.stream().filter(status -> status == 200).count())
                .isLessThan(statuses.size());
        // The limiter's 429 has no body; the global filter adds Retry-After.
        assertThat(retryAfter).isEqualTo("1");

        // A different client IP has its own bucket.
        assertThat(postStatus("/api/auth/login", null, nextIp())).isEqualTo(200);
    }

    @Test
    void policyDLimitsPublisherCreationPerClientIp() {
        String burstIp = nextIp();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            statuses.add(postStatus("/api/auth/publishers", null, burstIp));
        }

        assertThat(statuses.getFirst()).isEqualTo(200);
        assertThat(statuses).contains(429);
        assertThat(statuses.stream().filter(status -> status == 200).count()).isLessThan(10);

        // A different client IP can still create a publisher.
        assertThat(postStatus("/api/auth/publishers", null, nextIp())).isEqualTo(200);
    }

    @Test
    void policyELimitsPublicPageBurstPerClientIp() {
        String burstIp = nextIp();
        List<Integer> statuses = Flux.range(0, 200)
                .flatMap(index -> Mono.fromCallable(
                                () -> getStatus("/api/public/publishers/atlas", null, burstIp))
                        .subscribeOn(Schedulers.boundedElastic()), 32)
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(statuses).isNotNull();
        assertThat(statuses).contains(429);

        // A different client IP has its own bucket.
        assertThat(getStatus("/api/public/publishers/atlas", null, nextIp())).isEqualTo(200);
    }

    @Test
    void policyBCountsEachSubjectSeparatelyRegardlessOfTenant() {
        String alice = TOKENS.publisherToken("alice", "TENANT_ADMIN");
        String bob = TOKENS.publisherToken("bob", "TENANT_ADMIN");
        String learner = TOKENS.learnerToken("carol");

        // 120 concurrent requests exceed the burst of 40 by a wide margin even if the machine is
        // slow enough to refill a few tokens (20/s) while the burst is in flight.
        List<Integer> statuses = Flux.range(0, 120)
                .flatMap(index -> Mono.fromCallable(
                                () -> getStatus("/api/courses/1", alice, nextIp()))
                        .subscribeOn(Schedulers.boundedElastic()), 32)
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(statuses).isNotNull();
        assertThat(statuses).contains(200).contains(429);

        // Same publisher, different subject: a fresh bucket.
        get("/api/courses/1", bob).expectStatus().isOk();
        // A learner without tenant claims is keyed by sub as well.
        get("/api/courses/1", learner).expectStatus().isOk();
    }

    // ------------------------------------------------------- restricted session

    @Test
    void aTemporaryPasswordSessionCanOnlyChangeItsPasswordOrLogOut() {
        String restricted = TOKENS.mustChangePasswordToken("temporary-teacher");

        get("/api/auth/me", restricted)
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"status\":403")
                        .contains("Password change required.")
                        .doesNotContain("\"properties\":null", "\"type\":null"));
        get("/api/auth/publishers/slug-available?slug=free", restricted)
                .expectStatus().isForbidden();
        post("/api/auth/publishers", restricted).expectStatus().isForbidden();

        // The two allowed calls reach auth-service.
        post("/api/auth/password", restricted)
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"auth\""));
        post("/api/auth/logout", restricted)
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"service\":\"auth\""));
    }

    // ------------------------------------------------------- header hygiene

    @Test
    void stripsSpoofedTenantAndUserHeaders() {
        COURSE.clear();
        String token = TOKENS.publisherToken("user-headers", "TENANT_ADMIN");

        client.get().uri("/api/courses/1")
                .header(X_FORWARDED_FOR, nextIp())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-Id", "spoofed-tenant")
                .header("X-Tenant-Slug", "acme")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "SUPER_ADMIN")
                .exchange()
                .expectStatus().isOk();

        StubUpstream.Recorded recorded = COURSE.requests().getFirst();
        assertThat(recorded.header("X-Tenant-Id")).isNull();
        assertThat(recorded.header("X-Tenant-Slug")).isNull();
        assertThat(recorded.header("X-User-Id")).isNull();
        assertThat(recorded.header("X-User-Role")).isNull();
        // Zero trust: the token is forwarded unchanged for the downstream service to validate.
        assertThat(recorded.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    // ------------------------------------------------------------- management

    @Test
    void exposesPrometheusOnManagementPortOnly() {
        get("/api/courses/1", TOKENS.learnerToken("user-metrics")).expectStatus().isOk();

        WebTestClient managementClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + managementPort)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        managementClient.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("application=\"gateway\"")
                        .doesNotContain("tenant_id"));

        client.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
    }

    // ---------------------------------------------------------------- helpers

    private WebTestClient.ResponseSpec get(String path, String token) {
        return client.get().uri(path)
                .header(X_FORWARDED_FOR, nextIp())
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange();
    }

    private WebTestClient.ResponseSpec post(String path, String token) {
        return client.post().uri(path)
                .header(X_FORWARDED_FOR, nextIp())
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange();
    }

    private WebTestClient.ResponseSpec after(String path, String authorization) {
        return client.get().uri(path)
                .header(X_FORWARDED_FOR, nextIp())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }

    private int getStatus(String path, String token, String clientIp) {
        return client.get().uri(path)
                .header(X_FORWARDED_FOR, clientIp)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus()
                .value();
    }

    private int postStatus(String path, String token, String clientIp) {
        return client.post().uri(path)
                .header(X_FORWARDED_FOR, clientIp)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus()
                .value();
    }

    private static String nextIp() {
        return "198.18.0." + IP_COUNTER.incrementAndGet();
    }
}
