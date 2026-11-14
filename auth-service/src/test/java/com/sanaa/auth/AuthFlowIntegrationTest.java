package com.sanaa.auth;

import java.io.ByteArrayOutputStream;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.auth.config.JwtKeyProvider;
import com.sanaa.auth.domain.AdminAuditLog;
import com.sanaa.auth.domain.Membership;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.MembershipStatus;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.outbox.OutboxEventRepository;
import com.sanaa.auth.repository.AdminAuditLogRepository;
import com.sanaa.auth.repository.ImageRepository;
import com.sanaa.auth.repository.MembershipRepository;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.service.AuditService;
import com.sanaa.common.security.JwtClaims;
import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * End-to-end HTTP tests for the v2 identity and publisher model against a real MySQL instance.
 *
 * <p>The JWT key pair is generated at test startup and written to temporary files, so no private
 * key is committed. The demo profile is active to exercise the demo seed, the platform bootstrap is
 * enabled with a throwaway super admin, and the outbox poller is disabled so that outbox assertions
 * see the rows before publication (Kafka publication is covered by
 * {@link com.sanaa.auth.OutboxKafkaIntegrationTest}).
 *
 * <p>Each test creates its own publisher and accounts with a random suffix, so tests do not depend
 * on each other or on the order in which they run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Testcontainers
class AuthFlowIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PASSWORD = "correct horse battery";
    private static final String NEW_PASSWORD = "nouveau mot de passe solide";
    private static final String ADMIN_EMAIL = "root@sanaa.test";
    private static final String COOKIE_NAME = "sanaa_refresh";
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
            .withDatabaseName("auth_test")
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
        registry.add("sanaa.jwt.private-key-path", PRIVATE_KEY_PATH::toString);
        registry.add("sanaa.jwt.public-key-path", PUBLIC_KEY_PATH::toString);
        registry.add("sanaa.auth.refresh-cookie-secure", () -> "true");
        registry.add("management.server.port", () -> "0");
        registry.add("DEMO_PASSWORD", () -> PASSWORD);
        registry.add("sanaa.bootstrap.enabled", () -> "true");
        registry.add("sanaa.bootstrap.admin-email", () -> ADMIN_EMAIL);
        registry.add("sanaa.bootstrap.admin-password", () -> PASSWORD);
        registry.add("sanaa.bootstrap.admin-name", () -> "Platform Root");
        registry.add("sanaa.images.directory", IMAGES_DIRECTORY::toString);
        registry.add("sanaa.outbox.poller.enabled", () -> "false");
        // No broker is needed in this test; the closed port keeps the admin client from hanging.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("spring.kafka.admin.properties.default.api.timeout.ms", () -> "1000");
        registry.add("spring.kafka.admin.properties.request.timeout.ms", () -> "1000");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtKeyProvider jwtKeyProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditService auditService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
        reset(auditService);
    }

    // --------------------------------------------------------------- identity: registration

    @Test
    void registersALearnerAndLogsInWithoutATenantSlug() throws Exception {
        String email = unique("learner") + "@Example.COM";

        HttpResult register = post("/api/auth/register", body(
                "email", email,
                "password", PASSWORD,
                "fullName", "Alice Student"));
        assertThat(register.status()).isEqualTo(201);
        JsonNode registered = json(register);
        long userId = registered.get("id").asLong();
        assertThat(registered.get("email").asString())
                .isEqualTo(email.toLowerCase(java.util.Locale.ROOT));
        assertThat(registered.get("role").asString()).isEqualTo("STUDENT");
        assertThat(registered.has("tenantSlug")).isFalse();

        HttpResult login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        JsonNode loginBody = json(login);
        assertThat(loginBody.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(loginBody.size()).isEqualTo(2);
        String token = loginBody.get("accessToken").asString();

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(jwt.getClaimAsString("tenant_id")).isNull();
        assertThat(jwt.getClaimAsString("tenant_slug")).isNull();
        assertThat(jwt.getClaimAsString("tenant_type")).isNull();
        assertThat((Boolean) jwt.getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isFalse();
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("sanaa-auth");
        assertThat(jwt.getAudience()).containsExactly("sanaa-api");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isEqualTo(Duration.ofMinutes(15).getSeconds());

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(jwtKeyProvider.keyId());

        HttpResult me = get("/api/auth/me", token);
        assertThat(me.status()).isEqualTo(200);
        JsonNode meBody = json(me);
        assertThat(meBody.get("id").asLong()).isEqualTo(userId);
        assertThat(meBody.get("role").asString()).isEqualTo("STUDENT");
        assertThat(meBody.has("tenantId")).isFalse();
        assertThat(meBody.has("tenantSlug")).isFalse();
        assertThat(meBody.has("tenantType")).isFalse();

        assertProblem(get("/api/auth/me", null), 401, "");
    }

    @Test
    void loginFailsGenericallyForUnknownWrongPasswordAndDisabledAccounts() throws Exception {
        String email = unique("generic") + "@example.local";
        register(email, "Generic User");

        HttpResult unknown = login(unique("nobody") + "@example.local", PASSWORD);
        HttpResult wrongPassword = login(email, "definitely wrong password");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(json(unknown).get("detail").asString())
                .isEqualTo("Invalid email or password.");
        assertThat(json(unknown).get("detail").asString())
                .isEqualTo(json(wrongPassword).get("detail").asString());
        assertThat(unknown.body()).doesNotContain("\"properties\":null", "\"type\":null");

        User user = userRepository.findByEmail(email).orElseThrow();
        user.changeStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(user);

        HttpResult disabled = login(email, PASSWORD);
        assertThat(disabled.status()).isEqualTo(401);
        assertThat(json(disabled).get("detail").asString())
                .isEqualTo("Invalid email or password.");
    }

    @Test
    void registerCountsPasswordBytesNotCharacters() throws Exception {
        // 9 ASCII bytes: too short.
        assertThat(post("/api/auth/register", body(
                "email", unique("short") + "@example.local",
                "password", "123456789",
                "fullName", "Short Password")).status()).isEqualTo(400);

        // 73 ASCII bytes: above the BCrypt limit.
        assertThat(post("/api/auth/register", body(
                "email", unique("long") + "@example.local",
                "password", "a".repeat(73),
                "fullName", "Long Password")).status()).isEqualTo(400);

        // 19 four-byte emoji are 76 bytes but only 19 characters: the byte rule must reject them.
        assertThat(post("/api/auth/register", body(
                "email", unique("emoji") + "@example.local",
                "password", "\uD83D\uDE00".repeat(19),
                "fullName", "Emoji Password")).status()).isEqualTo(400);

        // Exactly 72 bytes is accepted.
        assertThat(post("/api/auth/register", body(
                "email", unique("limit") + "@example.local",
                "password", "\uD83D\uDE00".repeat(18),
                "fullName", "Limit Password")).status()).isEqualTo(201);
    }

    @Test
    void registerRejectsADuplicateEmailIncludingConcurrentAttempts() throws Exception {
        String email = unique("duplicate") + "@example.local";
        register(email, "First Owner");

        HttpResult duplicate = post("/api/auth/register", body(
                "email", email.toUpperCase(java.util.Locale.ROOT),
                "password", PASSWORD,
                "fullName", "Second Owner"));
        assertProblem(duplicate, 409, "already registered");

        String racingEmail = unique("race") + "@example.local";
        List<Integer> statuses = runConcurrently(2, index -> post("/api/auth/register", body(
                "email", racingEmail,
                "password", PASSWORD,
                "fullName", "Race " + index)).status());
        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(1);
    }

    // ------------------------------------------------------------------- identity: claims

    @Test
    void issuesPublisherClaimsForTenantAdminAndInstructor() throws Exception {
        String slug = unique("org");
        String ownerEmail = unique("owner") + "@example.local";
        HttpResult created = createAnonymousPublisher(slug, TenantType.ORGANIZATION,
                "Organisation " + slug, ownerEmail);
        assertThat(created.status()).isEqualTo(201);

        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));
        Jwt ownerJwt = jwtDecoder.decode(ownerToken);
        assertThat(ownerJwt.getClaimAsString("role")).isEqualTo("TENANT_ADMIN");
        assertThat(ownerJwt.getClaimAsString("tenant_slug")).isEqualTo(slug);
        assertThat(ownerJwt.getClaimAsString("tenant_type")).isEqualTo("ORGANIZATION");

        String instructorEmail = unique("teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Yasmine Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        assertThat(added.status()).isEqualTo(201);

        String temporaryToken = tokenOf(login(instructorEmail, PASSWORD));
        assertThat((Boolean) jwtDecoder.decode(temporaryToken)
                .getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isTrue();

        HttpResult changed = postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), temporaryToken);
        assertThat(changed.status()).isEqualTo(200);
        Jwt instructorJwt = jwtDecoder.decode(json(changed).get("accessToken").asString());
        assertThat(instructorJwt.getClaimAsString("role")).isEqualTo("INSTRUCTOR");
        assertThat(instructorJwt.getClaimAsString("tenant_id"))
                .isEqualTo(ownerJwt.getClaimAsString("tenant_id"));
        assertThat(instructorJwt.getClaimAsString("tenant_type")).isEqualTo("ORGANIZATION");

        String superAdminToken = tokenOf(login(ADMIN_EMAIL, PASSWORD));
        Jwt superAdmin = jwtDecoder.decode(superAdminToken);
        assertThat(superAdmin.getClaimAsString("role")).isEqualTo("SUPER_ADMIN");
        assertThat(superAdmin.getClaimAsString("tenant_id")).isNull();
    }

    @Test
    void jwksPublishesOnlyThePublicKey() throws Exception {
        HttpResult jwks = get("/.well-known/jwks.json", null);
        assertThat(jwks.status()).isEqualTo(200);
        JsonNode key = json(jwks).get("keys").get(0);
        assertThat(key.get("kty").asString()).isEqualTo("RSA");
        assertThat(key.get("d")).isNull();
        assertThat(key.get("p")).isNull();
        assertThat(key.get("q")).isNull();
        assertThat(key.get("kid").asString()).isEqualTo(jwtKeyProvider.keyId());

        String token = tokenOf(login(ADMIN_EMAIL, PASSWORD));
        RSAKey rsaKey = (RSAKey) JWK.parse(key.toString());
        assertThat(SignedJWT.parse(token)
                .verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();
    }

    // ------------------------------------------------------------------ publishers

    @Test
    void createsBothPublisherTypesAnonymouslyAndWritesTheOutboxRow() throws Exception {
        for (TenantType type : TenantType.values()) {
            String slug = unique(type.name().toLowerCase(java.util.Locale.ROOT));
            String email = unique("creator") + "@example.local";
            HttpResult created = createAnonymousPublisher(slug, type,
                    "Publisher " + slug, email);
            assertThat(created.status()).isEqualTo(201);
            JsonNode body = json(created);
            assertThat(body.size()).isEqualTo(3);
            assertThat(body.get("slug").asString()).isEqualTo(slug);
            assertThat(body.get("type").asString()).isEqualTo(type.name());
            assertThat(created.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(created.body()).doesNotContain("accessToken", "sanaa_refresh");

            Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
            assertThat(tenant.getType()).isEqualTo(type);

            var outbox = outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId());
            assertThat(outbox).hasSize(1);
            assertThat(outbox.getFirst().getTenantId()).isEqualTo(tenant.getId());
            assertThat(outbox.getFirst().getTopic()).isEqualTo("sanaa.publishers.v1");
            JsonNode envelope = JSON.readTree(outbox.getFirst().getPayload());
            assertThat(envelope.get("eventType").asString()).isEqualTo("PUBLISHER_UPSERTED");
            assertThat(envelope.get("tenantId").asString()).isEqualTo(tenant.getId());
            assertThat(envelope.get("payload").get("slug").asString()).isEqualTo(slug);
            assertThat(envelope.get("payload").get("type").asString())
                    .isEqualTo(type.name());

            HttpResult login = login(email, PASSWORD);
            assertThat(login.status()).isEqualTo(200);
            assertThat(jwtDecoder.decode(tokenOf(login)).getClaimAsString("role"))
                    .isEqualTo("TENANT_ADMIN");
        }
    }

    @Test
    void letsALoggedInLearnerBecomeAPublisherAndRefreshReEvaluatesClaims() throws Exception {
        String email = unique("become") + "@example.local";
        register(email, "Future Publisher");
        HttpResult login = login(email, PASSWORD);
        String learnerToken = tokenOf(login);

        String slug = unique("individual");
        HttpResult created = postJson("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "Studio " + slug,
                "slug", slug), learnerToken);
        assertThat(created.status()).isEqualTo(201);

        // The already-issued access token keeps its old claims until it expires; the refresh is
        // where the new membership must become visible.
        assertThat(jwtDecoder.decode(learnerToken).getClaimAsString("role"))
                .isEqualTo("STUDENT");

        HttpResult refreshed = refresh(refreshCookie(login));
        assertThat(refreshed.status()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(tokenOf(refreshed));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TENANT_ADMIN");
        assertThat(jwt.getClaimAsString("tenant_slug")).isEqualTo(slug);
        assertThat(jwt.getClaimAsString("tenant_type")).isEqualTo("INDIVIDUAL");

        // A member cannot join a second publisher.
        assertProblem(postJson("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "Second Studio",
                "slug", unique("second")), tokenOf(refreshed)), 409, "already belongs");
    }

    @Test
    void aLoggedInLearnerCanCreateAnOrganizationPublisherAndManageItsTeam() throws Exception {
        String email = unique("become-org") + "@example.local";
        register(email, "Future Studio Owner");
        HttpResult login = login(email, PASSWORD);
        String slug = unique("logged-org");

        HttpResult created = postJson("/api/auth/publishers", body(
                "publisherType", "ORGANIZATION",
                "publisherName", "Studio " + slug,
                "slug", slug), tokenOf(login));
        assertThat(created.status()).isEqualTo(201);
        assertThat(json(created).get("type").asString()).isEqualTo("ORGANIZATION");

        HttpResult refreshed = refresh(refreshCookie(login));
        assertThat(refreshed.status()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(tokenOf(refreshed));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TENANT_ADMIN");
        assertThat(jwt.getClaimAsString("tenant_slug")).isEqualTo(slug);
        assertThat(jwt.getClaimAsString("tenant_type")).isEqualTo("ORGANIZATION");

        String token = tokenOf(refreshed);
        assertThat(postJson("/api/auth/team", body(
                "fullName", "Premier Enseignant",
                "email", unique("first-teacher") + "@example.local",
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), token).status()).isEqualTo(201);
        assertThat(json(get("/api/auth/team", token)).size()).isEqualTo(2);
    }

    @Test
    void tenParallelCreationsOfOneSlugProduceExactlyOnePublisher() throws Exception {
        String slug = unique("race-slug");
        List<Integer> statuses = runConcurrently(10, index -> createAnonymousPublisher(
                slug, TenantType.INDIVIDUAL, "Race Studio", unique("race" + index) + "@example.local")
                .status());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(9);
        assertThat(tenantRepository.findBySlug(slug)).isPresent();
    }

    @Test
    void checksSlugAvailabilityAndValidatesSlugRules() throws Exception {
        HttpResult reserved = get("/api/auth/publishers/slug-available?slug=admin", null);
        assertThat(reserved.status()).isEqualTo(200);
        assertThat(json(reserved).get("available").asBoolean()).isFalse();

        HttpResult invalid = get("/api/auth/publishers/slug-available?slug=A%21", null);
        assertThat(json(invalid).get("available").asBoolean()).isFalse();

        String slug = unique("free");
        HttpResult free = get("/api/auth/publishers/slug-available?slug=" + slug, null);
        assertThat(json(free).get("available").asBoolean()).isTrue();

        assertThat(createAnonymousPublisher(slug, TenantType.INDIVIDUAL, "Free Studio",
                unique("free") + "@example.local").status()).isEqualTo(201);

        HttpResult taken = get("/api/auth/publishers/slug-available?slug=" + slug, null);
        assertThat(json(taken).get("available").asBoolean()).isFalse();

        assertProblem(post("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "Bad Slug",
                "slug", "Not A Slug")), 400, "");
        assertProblem(post("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "Reserved Slug",
                "slug", "support",
                "ownerFullName", "Reserved Owner",
                "ownerEmail", unique("reserved") + "@example.local",
                "password", PASSWORD)), 400, "reserved");
    }

    @Test
    void anonymousCreationRequiresOwnerFieldsAndLoggedInCreationForbidsThem() throws Exception {
        assertProblem(post("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "No Owner",
                "slug", unique("no-owner"))), 400, "Owner name, email and password");

        String email = unique("session") + "@example.local";
        register(email, "Session Owner");
        String token = tokenOf(login(email, PASSWORD));

        assertProblem(postJson("/api/auth/publishers", body(
                "publisherType", "INDIVIDUAL",
                "publisherName", "With Owner Fields",
                "slug", unique("with-owner"),
                "ownerFullName", "Duplicate Owner",
                "ownerEmail", unique("dup") + "@example.local",
                "password", PASSWORD), token), 400, "must not be sent");
    }

    @Test
    void refreshDowngradesWhenThePublisherIsSuspendedOrTheMembershipIsDisabled() throws Exception {
        String slug = unique("susp");
        String email = unique("suspadmin") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Suspend Studio", email);
        HttpResult login = login(email, PASSWORD);
        assertThat(jwtDecoder.decode(tokenOf(login)).getClaimAsString("role"))
                .isEqualTo("TENANT_ADMIN");

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        tenant.suspend();
        tenantRepository.saveAndFlush(tenant);

        HttpResult downgraded = refresh(refreshCookie(login));
        assertThat(downgraded.status()).isEqualTo(200);
        Jwt downgradedJwt = jwtDecoder.decode(tokenOf(downgraded));
        assertThat(downgradedJwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(downgradedJwt.getClaimAsString("tenant_id")).isNull();

        tenant.activate();
        tenantRepository.saveAndFlush(tenant);

        HttpResult restored = refresh(refreshCookie(downgraded));
        Jwt restoredJwt = jwtDecoder.decode(tokenOf(restored));
        assertThat(restoredJwt.getClaimAsString("role")).isEqualTo("TENANT_ADMIN");

        long userId = Long.parseLong(restoredJwt.getSubject());
        TenantContext.runAs(tenant.getId(), () -> {
            Membership membership = membershipRepository.findByUserId(userId).orElseThrow();
            membership.changeStatus(MembershipStatus.DISABLED);
            membershipRepository.saveAndFlush(membership);
        });

        HttpResult disabledMembership = refresh(refreshCookie(restored));
        assertThat(jwtDecoder.decode(tokenOf(disabledMembership)).getClaimAsString("role"))
                .isEqualTo("STUDENT");
    }

    @Test
    void disabledUserCannotLoginOrRefresh() throws Exception {
        String email = unique("disabled") + "@example.local";
        register(email, "Disabled User");
        HttpResult login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.changeStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(user);

        assertProblem(login(email, PASSWORD), 401, "");
        assertProblem(refresh(refreshCookie(login)), 401, "");
    }

    // --------------------------------------------------------------- password and session

    @Test
    void temporaryPasswordRestrictsTheSessionUntilThePasswordIsChanged() throws Exception {
        String slug = unique("restricted");
        String ownerEmail = unique("restricted-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Restricted Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));

        String instructorEmail = unique("restricted-teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Temporary Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        assertThat(added.status()).isEqualTo(201);

        HttpResult login = login(instructorEmail, PASSWORD);
        String restrictedToken = tokenOf(login);
        assertThat((Boolean) jwtDecoder.decode(restrictedToken)
                .getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isTrue();

        assertProblem(get("/api/auth/me", restrictedToken), 403, "Password change required.");
        assertProblem(get("/api/auth/profile", restrictedToken), 403, "Password change required.");
        assertProblem(get("/api/auth/team", restrictedToken), 403, "Password change required.");

        HttpResult logout = postWithCookie("/api/auth/logout", refreshCookie(login));
        assertThat(logout.status()).isEqualTo(204);

        assertProblem(postJson("/api/auth/password", body(
                "currentPassword", "wrong current password",
                "newPassword", NEW_PASSWORD), restrictedToken), 401, "");

        HttpResult changed = postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), restrictedToken);
        assertThat(changed.status()).isEqualTo(200);
        String newToken = json(changed).get("accessToken").asString();
        assertThat((Boolean) jwtDecoder.decode(newToken)
                .getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isFalse();

        // The response carries a fresh cookie, so the current tab keeps working.
        assertThat(refresh(refreshCookie(changed)).status()).isEqualTo(200);
        // Every old refresh session is revoked. Replaying one is a reuse event, so it also ends
        // the other sessions (including the fresh one); the access token stays valid until expiry.
        assertProblem(refresh(refreshCookie(login)), 401, "");
        assertThat(get("/api/auth/me", newToken).status()).isEqualTo(200);

        assertThat(login(instructorEmail, NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(login(instructorEmail, PASSWORD).status()).isEqualTo(401);
    }

    // ------------------------------------------------------------------------ profiles

    @Test
    void updatesTheOwnProfileAndRejectsUnsafeLinks() throws Exception {
        String email = unique("profile") + "@example.local";
        register(email, "Profile User");
        String token = tokenOf(login(email, PASSWORD));

        HttpResult updated = putJson("/api/auth/profile", body(
                "fullName", "Profil Français",
                "headline", "Apprenante en développement web",
                "bio", "Je me forme au développement web.",
                "websiteUrl", "https://example.test/portfolio",
                "instagramUrl", "https://instagram.com/profil.demo",
                "linkedinUrl", "https://www.linkedin.com/in/profil-demo",
                "youtubeUrl", "https://youtube.com/@profil-demo"), token);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(json(updated).get("headline").asString())
                .isEqualTo("Apprenante en développement web");

        JsonNode read = json(get("/api/auth/profile", token));
        assertThat(read.get("websiteUrl").asString()).isEqualTo("https://example.test/portfolio");
        assertThat(read.get("fullName").asString()).isEqualTo("Profil Français");

        for (String unsafe : List.of(
                "javascript:alert(1)",
                "http://example.test/insecure",
                "https://user:password@example.test/leak",
                "https://example.test/" + "a".repeat(190))) {
            assertProblem(putJson("/api/auth/profile", body("websiteUrl", unsafe), token),
                    400, "");
        }

        assertProblem(putJson("/api/auth/profile", body("headline", "a".repeat(121)), token),
                400, "");
    }

    @Test
    void anInstructorProfileChangeEmitsInstructorUpdated() throws Exception {
        String slug = unique("instructor-event");
        String ownerEmail = unique("ie-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Event Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();

        String instructorEmail = unique("ie-teacher") + "@example.local";
        postJson("/api/auth/team", body(
                "fullName", "Event Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        HttpResult temporary = login(instructorEmail, PASSWORD);
        HttpResult changed = postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), tokenOf(temporary));
        String instructorToken = json(changed).get("accessToken").asString();

        HttpResult profile = putJson("/api/auth/profile", body(
                "fullName", "Professeure Émilie",
                "headline", "Formatrice en data"), instructorToken);
        assertThat(profile.status()).isEqualTo(200);

        var events = outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId());
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getTenantId()).isEqualTo(tenant.getId());
            JsonNode envelope = JSON.readTree(event.getPayload());
            assertThat(envelope.get("eventType").asString()).isEqualTo("INSTRUCTOR_UPDATED");
            assertThat(envelope.get("payload").get("name").asString())
                    .isEqualTo("Professeure Émilie");
        });
    }

    @Test
    void updatesThePublisherProfileAndForbidsNonAdmins() throws Exception {
        String slug = unique("brand");
        String email = unique("brand") + "@example.local";
        createAnonymousPublisher(slug, TenantType.INDIVIDUAL, "Brand Studio", email);
        String adminToken = tokenOf(login(email, PASSWORD));

        HttpResult updated = putJson("/api/auth/publishers/me", body(
                "name", "Studio Élégant",
                "headline", "Créations visuelles",
                "description", "Un studio indépendant spécialisé dans l'identité visuelle.",
                "websiteUrl", "https://studio-elegant.test",
                "instagramUrl", "https://instagram.com/studio.elegant",
                "linkedinUrl", "https://www.linkedin.com/company/studio-elegant",
                "youtubeUrl", "https://youtube.com/@studio-elegant",
                "primaryColor", "#195A56",
                "contactEmail", "bonjour@studio-elegant.test"), adminToken);
        assertThat(updated.status()).isEqualTo(200);
        JsonNode body = json(updated);
        assertThat(body.get("name").asString()).isEqualTo("Studio Élégant");
        assertThat(body.get("primaryColor").asString()).isEqualTo("#195A56");

        JsonNode publicPage = json(get("/api/public/publishers/" + slug, null));
        assertThat(publicPage.get("name").asString()).isEqualTo("Studio Élégant");
        assertThat(publicPage.get("description").asString()).contains("identité visuelle");

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        assertThat(outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId()))
                .hasSize(2);

        String learnerEmail = unique("brand-learner") + "@example.local";
        register(learnerEmail, "Brand Learner");
        assertProblem(putJson("/api/auth/publishers/me", body(
                "name", "Hijack", "primaryColor", "#000000"),
                tokenOf(login(learnerEmail, PASSWORD))), 403, "");
    }

    // -------------------------------------------------------------------------- images

    @Test
    void uploadsAndServesAnAvatarDetectedFromMagicBytes() throws Exception {
        String email = unique("avatar") + "@example.local";
        register(email, "Avatar User");
        String token = tokenOf(login(email, PASSWORD));

        byte[] png = pngBytes();
        HttpResult upload = postImage("avatar", "photo.png", "image/png", png, token);
        assertThat(upload.status()).isEqualTo(201);
        JsonNode body = json(upload);
        String imageId = body.get("id").asString();
        assertThat(body.get("contentType").asString()).isEqualTo("image/png");
        assertThat(body.get("sizeBytes").asLong()).isEqualTo(png.length);

        assertThat(json(get("/api/auth/profile", token)).get("avatarImageId").asString())
                .isEqualTo(imageId);

        HttpResponse<byte[]> image = sendBytes(URI.create(
                baseUrl() + "/api/public/images/" + imageId));
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(image.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("max-age=3600"));
        assertThat(image.body()).isEqualTo(png);

        assertProblem(get("/api/public/images/" + UUID.randomUUID(), null), 404, "");
        assertProblem(postImage("avatar", "photo.png", "image/png", png, null), 401, "");
    }

    @Test
    void rejectsFilesThatAreNotRealImagesAndFilesOverOneMegabyte() throws Exception {
        String email = unique("bad-image") + "@example.local";
        register(email, "Bad Image");
        String token = tokenOf(login(email, PASSWORD));

        HttpResult renamedText = postImage("avatar", "photo.png", "image/png",
                "this is not an image".getBytes(StandardCharsets.UTF_8), token);
        assertThat(renamedText.status()).isEqualTo(415);

        byte[] tooLarge = new byte[1_048_577];
        byte[] signature = pngBytes();
        System.arraycopy(signature, 0, tooLarge, 0, signature.length);
        assertThat(postImage("avatar", "huge.png", "image/png", tooLarge, token).status())
                .isEqualTo(413);

        assertProblem(postImage("picture", "photo.png", "image/png", pngBytes(), token),
                400, "avatar or logo");
    }

    @Test
    void logosRequireAPublisherAdminAndReplacingOneDeletesTheOldImage() throws Exception {
        String slug = unique("logo");
        String adminEmail = unique("logo-admin") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Logo Studio", adminEmail);
        String adminToken = tokenOf(login(adminEmail, PASSWORD));

        String learnerEmail = unique("logo-learner") + "@example.local";
        register(learnerEmail, "Logo Learner");
        assertProblem(postImage("logo", "logo.png", "image/png", pngBytes(),
                tokenOf(login(learnerEmail, PASSWORD))), 403, "publisher admin");

        HttpResult firstUpload = postImage("logo", "logo.png", "image/png", pngBytes(),
                adminToken);
        assertThat(firstUpload.status()).isEqualTo(201);
        String firstId = json(firstUpload).get("id").asString();
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        assertThat(tenant.getLogoImageId()).isEqualTo(firstId);

        byte[] secondPng = pngBytesWithDifferentContent();
        HttpResult secondUpload = postImage("logo", "logo-2.png", "image/png", secondPng,
                adminToken);
        assertThat(secondUpload.status()).isEqualTo(201);
        String secondId = json(secondUpload).get("id").asString();

        assertThat(imageRepository.findById(firstId)).isEmpty();
        assertProblem(get("/api/public/images/" + firstId, null), 404, "");
        assertThat(get("/api/public/images/" + secondId, null).status()).isEqualTo(200);
    }

    // ---------------------------------------------------------------------------- team

    @Test
    void teamManagementIsOrganizationOnlyAndAdminOnly() throws Exception {
        String individualSlug = unique("solo");
        String individualEmail = unique("solo") + "@example.local";
        createAnonymousPublisher(individualSlug, TenantType.INDIVIDUAL, "Solo Studio",
                individualEmail);
        String individualToken = tokenOf(login(individualEmail, PASSWORD));
        assertProblem(get("/api/auth/team", individualToken), 409, "organization");

        String learnerEmail = unique("team-learner") + "@example.local";
        register(learnerEmail, "Team Learner");
        assertProblem(get("/api/auth/team", tokenOf(login(learnerEmail, PASSWORD))), 403, "");

        String orgSlug = unique("team-org");
        String ownerEmail = unique("team-owner") + "@example.local";
        createAnonymousPublisher(orgSlug, TenantType.ORGANIZATION, "Team Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));

        String instructorEmail = unique("team-teacher") + "@example.local";
        assertThat(postJson("/api/auth/team", body(
                "fullName", "Team Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken).status()).isEqualTo(201);
        HttpResult temporary = login(instructorEmail, PASSWORD);
        String instructorToken = json(postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), tokenOf(temporary)))
                .get("accessToken").asString();
        assertProblem(get("/api/auth/team", instructorToken), 403, "");
    }

    @Test
    void teamAddValidatesTemporaryPasswordAndDuplicateEmails() throws Exception {
        String slug = unique("add");
        String ownerEmail = unique("add-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Add Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));

        String email = unique("add-teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Added Teacher",
                "email", email,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        assertThat(added.status()).isEqualTo(201);
        assertThat(json(added).get("mustChangePassword").asBoolean()).isTrue();

        assertProblem(postJson("/api/auth/team", body(
                "fullName", "Duplicate Teacher",
                "email", email,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken), 409, "already registered");

        assertProblem(postJson("/api/auth/team", body(
                "fullName", "Short Password",
                "email", unique("add-short") + "@example.local",
                "role", "INSTRUCTOR",
                "temporaryPassword", "123456789"), ownerToken), 400, "10 and 72");

        JsonNode list = json(get("/api/auth/team", ownerToken));
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void teamListIsCappedAtFiftyMembers() throws Exception {
        String slug = unique("cap");
        String ownerEmail = unique("cap-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Cap Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        String hash = passwordEncoder.encode(PASSWORD);

        TenantContext.runAs(tenant.getId(), () -> {
            for (int index = 0; index < 52; index++) {
                User user = userRepository.saveAndFlush(new User(
                        "cap" + index + "-" + tenant.getSlug() + "@example.local", hash,
                        "Cap Member " + index, PlatformRole.USER));
                membershipRepository.saveAndFlush(
                        new Membership(user.getId(), MembershipRole.INSTRUCTOR));
            }
        });

        JsonNode list = json(get("/api/auth/team", ownerToken));
        assertThat(list.size()).isEqualTo(50);
    }

    @Test
    void teamListNeverContainsAnotherTenantsRows() throws Exception {
        IsolationFixture fixture = isolationFixture();

        JsonNode list = json(get("/api/auth/team", fixture.ownerBToken()));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("userId").asLong()).isEqualTo(fixture.ownerBId());
        assertThat(get("/api/auth/team", fixture.ownerBToken()).body())
                .doesNotContain(fixture.memberEmail());
        // The tenant filter also applies to the repository behind the list, not only to the DTO.
        assertThat(TenantContext.callAs(fixture.tenantBId(),
                () -> membershipRepository.findByUserId(fixture.memberId()).isPresent())).isFalse();
    }

    @Test
    void anotherTenantCannotDisableAMember() throws Exception {
        IsolationFixture fixture = isolationFixture();

        assertProblem(patchJson("/api/auth/team/" + fixture.memberId() + "/status",
                body("status", "DISABLED"), fixture.ownerBToken()), 404, "not found");

        assertThat(membershipOf(fixture.tenantAId(), fixture.memberId()).getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
        assertThat(login(fixture.memberEmail(), PASSWORD).status()).isEqualTo(200);
        // The failed attempt must not revoke the member's sessions either.
        assertThat(refresh(fixture.memberCookie()).status()).isEqualTo(200);
    }

    @Test
    void anotherTenantCannotEnableAMember() throws Exception {
        IsolationFixture fixture = isolationFixture();
        assertThat(patchJson("/api/auth/team/" + fixture.memberId() + "/status",
                body("status", "DISABLED"), fixture.ownerAToken()).status()).isEqualTo(200);

        assertProblem(patchJson("/api/auth/team/" + fixture.memberId() + "/status",
                body("status", "ACTIVE"), fixture.ownerBToken()), 404, "not found");

        assertThat(membershipOf(fixture.tenantAId(), fixture.memberId()).getStatus())
                .isEqualTo(MembershipStatus.DISABLED);
        assertProblem(refresh(fixture.memberCookie()), 401, "");
    }

    @Test
    void anotherTenantCannotResetAMembersPassword() throws Exception {
        IsolationFixture fixture = isolationFixture();

        assertProblem(postJson("/api/auth/team/" + fixture.memberId() + "/reset-password",
                body("temporaryPassword", NEW_PASSWORD), fixture.ownerBToken()), 404, "not found");

        // The member keeps the password from A's invitation and the forged one never worked.
        assertThat(login(fixture.memberEmail(), PASSWORD).status()).isEqualTo(200);
        assertProblem(login(fixture.memberEmail(), NEW_PASSWORD), 401, "");
    }

    /**
     * Publisher A with one invited member, plus an unrelated publisher B. Everything a cross-tenant
     * test needs is created through the real HTTP API, so no fixture bypasses the tenant context.
     */
    private IsolationFixture isolationFixture() throws Exception {
        String slugA = unique("iso-a");
        String ownerAEmail = unique("iso-a-owner") + "@example.local";
        createAnonymousPublisher(slugA, TenantType.ORGANIZATION, "Isolation A", ownerAEmail);
        String ownerAToken = tokenOf(login(ownerAEmail, PASSWORD));

        String memberEmail = unique("iso-member") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Isolation Member",
                "email", memberEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerAToken);
        assertThat(added.status()).isEqualTo(201);
        long memberId = json(added).get("userId").asLong();
        String memberCookie = refreshCookie(login(memberEmail, PASSWORD));

        String slugB = unique("iso-b");
        String ownerBEmail = unique("iso-b-owner") + "@example.local";
        createAnonymousPublisher(slugB, TenantType.ORGANIZATION, "Isolation B", ownerBEmail);
        String ownerBToken = tokenOf(login(ownerBEmail, PASSWORD));
        long ownerBId = Long.parseLong(jwtDecoder.decode(ownerBToken).getSubject());

        Tenant tenantA = tenantRepository.findBySlug(slugA).orElseThrow();
        Tenant tenantB = tenantRepository.findBySlug(slugB).orElseThrow();
        return new IsolationFixture(ownerAToken, ownerBToken, ownerBId, tenantA.getId(),
                tenantB.getId(), memberId, memberEmail, memberCookie);
    }

    private record IsolationFixture(String ownerAToken, String ownerBToken, long ownerBId,
                                    String tenantAId, String tenantBId, long memberId,
                                    String memberEmail, String memberCookie) {
    }

    @Test
    void disablingAMemberRevokesItsSessionsAndSelfDisableIsRefused() throws Exception {
        String slug = unique("disable");
        String ownerEmail = unique("disable-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Disable Studio", ownerEmail);
        HttpResult ownerLogin = login(ownerEmail, PASSWORD);
        String ownerToken = tokenOf(ownerLogin);
        long ownerId = Long.parseLong(jwtDecoder.decode(ownerToken).getSubject());

        String instructorEmail = unique("disable-teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Disable Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        long instructorId = json(added).get("userId").asLong();

        HttpResult instructorLogin = login(instructorEmail, PASSWORD);
        assertThat(patchJson("/api/auth/team/" + instructorId + "/status",
                body("status", "DISABLED"), ownerToken).status()).isEqualTo(200);
        assertProblem(refresh(refreshCookie(instructorLogin)), 401, "");

        assertProblem(patchJson("/api/auth/team/" + ownerId + "/status",
                body("status", "DISABLED"), ownerToken), 409, "your own account");
    }

    @Test
    void twoParallelAdminDisablesCannotRemoveTheLastAdmin() throws Exception {
        String slug = unique("last-admin");
        String firstEmail = unique("admin-one") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Last Admin Studio", firstEmail);
        String firstToken = tokenOf(login(firstEmail, PASSWORD));
        long firstId = Long.parseLong(jwtDecoder.decode(firstToken).getSubject());

        String secondEmail = unique("admin-two") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Second Admin",
                "email", secondEmail,
                "role", "TENANT_ADMIN",
                "temporaryPassword", PASSWORD), firstToken);
        long secondId = json(added).get("userId").asLong();

        HttpResult temporary = login(secondEmail, PASSWORD);
        String secondToken = json(postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), tokenOf(temporary)))
                .get("accessToken").asString();

        List<Integer> statuses = runConcurrently(2, index -> index == 0
                ? patchJson("/api/auth/team/" + secondId + "/status",
                        body("status", "DISABLED"), firstToken).status()
                : patchJson("/api/auth/team/" + firstId + "/status",
                        body("status", "DISABLED"), secondToken).status());

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(1);

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        long activeAdmins = TenantContext.callAs(tenant.getId(), () -> membershipRepository
                .countByRoleAndStatus(MembershipRole.TENANT_ADMIN, MembershipStatus.ACTIVE));
        assertThat(activeAdmins).isEqualTo(1);
    }

    @Test
    void resettingATeamPasswordRevokesSessionsAndForcesAChange() throws Exception {
        String slug = unique("reset");
        String ownerEmail = unique("reset-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Reset Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));

        String instructorEmail = unique("reset-teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Reset Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        long instructorId = json(added).get("userId").asLong();

        HttpResult oldSession = login(instructorEmail, PASSWORD);
        String resetPassword = "mot de passe reinitialise";
        assertThat(postJson("/api/auth/team/" + instructorId + "/reset-password",
                body("temporaryPassword", resetPassword), ownerToken).status()).isEqualTo(204);
        assertProblem(refresh(refreshCookie(oldSession)), 401, "");

        HttpResult fresh = login(instructorEmail, resetPassword);
        assertThat(json(fresh).get("accessToken").asString()).isNotBlank();
        assertThat((Boolean) jwtDecoder.decode(tokenOf(fresh))
                .getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isTrue();
    }

    // -------------------------------------------------------------------- public pages

    @Test
    void publicPublisherPageExposesTeamButNeverEmails() throws Exception {
        String slug = unique("public-org");
        String ownerEmail = unique("public-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Public Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));

        String instructorEmail = unique("public-teacher") + "@example.local";
        HttpResult added = postJson("/api/auth/team", body(
                "fullName", "Public Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken);
        long instructorId = json(added).get("userId").asLong();

        HttpResult page = get("/api/public/publishers/" + slug, null);
        assertThat(page.status()).isEqualTo(200);
        JsonNode body = json(page);
        assertThat(body.get("slug").asString()).isEqualTo(slug);
        assertThat(body.get("type").asString()).isEqualTo("ORGANIZATION");
        assertThat(body.get("team").size()).isEqualTo(2);
        assertThat(page.body()).doesNotContain("@", "\"email\"", "contactEmail");

        assertThat(get("/api/public/instructors/" + instructorId, null).status()).isEqualTo(200);
        HttpResult instructorPage = get("/api/public/instructors/" + instructorId, null);
        assertThat(instructorPage.body()).doesNotContain(instructorEmail, "\"email\"");

        assertProblem(get("/api/public/publishers/" + unique("missing"), null), 404, "");

        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();
        tenant.suspend();
        tenantRepository.saveAndFlush(tenant);
        assertProblem(get("/api/public/publishers/" + slug, null), 404, "");
        assertProblem(get("/api/public/instructors/" + instructorId, null), 404, "");
    }

    @Test
    void publicInstructorPageOnlyExistsForActivePublisherMembers() throws Exception {
        String learnerEmail = unique("public-learner") + "@example.local";
        HttpResult learner = post("/api/auth/register", body(
                "email", learnerEmail, "password", PASSWORD, "fullName", "Public Learner"));
        long learnerId = json(learner).get("id").asLong();
        assertProblem(get("/api/public/instructors/" + learnerId, null), 404, "");
    }

    // --------------------------------------------------------------------------- admin

    @Test
    void adminEndpointsRejectEveryOtherRole() throws Exception {
        String learnerEmail = unique("admin-learner") + "@example.local";
        register(learnerEmail, "Admin Learner");
        assertProblem(get("/api/admin/users", tokenOf(login(learnerEmail, PASSWORD))), 403, "");

        String ownerEmail = unique("admin-owner") + "@example.local";
        String slug = unique("admin-org");
        createAnonymousPublisher(slug, TenantType.ORGANIZATION, "Admin Studio", ownerEmail);
        String ownerToken = tokenOf(login(ownerEmail, PASSWORD));
        assertProblem(get("/api/admin/publishers", ownerToken), 403, "");

        String instructorEmail = unique("admin-teacher") + "@example.local";
        assertThat(postJson("/api/auth/team", body(
                "fullName", "Admin Teacher",
                "email", instructorEmail,
                "role", "INSTRUCTOR",
                "temporaryPassword", PASSWORD), ownerToken).status()).isEqualTo(201);
        String instructorToken = json(postJson("/api/auth/password", body(
                "currentPassword", PASSWORD,
                "newPassword", NEW_PASSWORD), tokenOf(login(instructorEmail, PASSWORD))))
                .get("accessToken").asString();
        assertProblem(get("/api/admin/users", instructorToken), 403, "");

        assertProblem(get("/api/admin/users", null), 401, "");
        assertThat(get("/api/admin/users", tokenOf(login(ADMIN_EMAIL, PASSWORD))).status())
                .isEqualTo(200);
    }

    @Test
    void adminReadsAreAuditedAndNeverExposePasswordHashes() throws Exception {
        String superAdminToken = tokenOf(login(ADMIN_EMAIL, PASSWORD));

        HttpResult users = get("/api/admin/users", superAdminToken);
        assertThat(users.status()).isEqualTo(200);
        assertThat(users.body()).doesNotContain("passwordHash", "password_hash");
        JsonNode first = json(users).get("items").get(0);
        long targetId = first.get("id").asLong();

        HttpResult detail = get("/api/admin/users/" + targetId, superAdminToken);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).doesNotContain("passwordHash", "password_hash");

        assertThat(auditCount("USER_LISTED")).isGreaterThanOrEqualTo(1);
        assertThat(auditCount("USER_READ")).isGreaterThanOrEqualTo(1);

        HttpResult publishers = get("/api/admin/publishers", superAdminToken);
        assertThat(publishers.status()).isEqualTo(200);
        assertThat(json(publishers).get("items").size()).isPositive();
        assertThat(auditCount("PUBLISHER_LISTED")).isGreaterThanOrEqualTo(1);

        HttpResult audit = get("/api/admin/audit/auth?size=50", superAdminToken);
        assertThat(audit.status()).isEqualTo(200);
        assertThat(json(audit).get("items").size()).isPositive();
        assertThat(auditCount("AUDIT_LISTED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminUserModerationRequiresAReasonAndCannotDisableTheActor() throws Exception {
        String superAdminToken = tokenOf(login(ADMIN_EMAIL, PASSWORD));
        long adminId = Long.parseLong(jwtDecoder.decode(superAdminToken).getSubject());

        assertProblem(patchJson("/api/admin/users/" + adminId + "/status",
                body("status", "DISABLED", "reason", "no"), superAdminToken), 400, "");

        assertProblem(patchJson("/api/admin/users/" + adminId + "/status",
                body("status", "DISABLED", "reason", "je quitte la plateforme"),
                superAdminToken), 409, "your own account");

        String email = unique("moderated") + "@example.local";
        HttpResult registered = post("/api/auth/register", body(
                "email", email, "password", PASSWORD, "fullName", "Moderated User"));
        long userId = json(registered).get("id").asLong();

        HttpResult changed = patchJson("/api/admin/users/" + userId + "/status",
                body("status", "DISABLED", "reason", "compte frauduleux signalé"),
                superAdminToken);
        assertThat(changed.status()).isEqualTo(200);
        assertThat(json(changed).get("status").asString()).isEqualTo("DISABLED");
        assertProblem(login(email, PASSWORD), 401, "");

        Integer audited = jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_log where action = 'USER_STATUS_CHANGED' "
                        + "and target_id = ? and reason = ?",
                Integer.class, Long.toString(userId), "compte frauduleux signalé");
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void aFailedAuditInsertRollsBackTheAdminAction() throws Exception {
        String superAdminToken = tokenOf(login(ADMIN_EMAIL, PASSWORD));
        String email = unique("audit-rollback") + "@example.local";
        register(email, "Audit Rollback");
        long userId = userRepository.findByEmail(email).orElseThrow().getId();

        // Fail closed: the audit row is part of the admin action's transaction, so an insert failure
        // must undo the status change instead of leaving an unaudited modification.
        doThrow(new IllegalStateException("audit insert failed"))
                .when(auditService).record(any(), eq(AuditService.USER_STATUS_CHANGED),
                        any(), any(), any(), any());
        try {
            HttpResult failed = patchJson("/api/admin/users/" + userId + "/status",
                    body("status", "DISABLED", "reason", "test de transaction"), superAdminToken);
            assertThat(failed.status()).isEqualTo(500);
            assertThat(failed.body()).doesNotContain("audit insert failed", "IllegalStateException");
        } finally {
            reset(auditService);
        }

        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(login(email, PASSWORD).status()).isEqualTo(200);

        // Once auditing works again the same action commits the change and exactly one audit row.
        assertThat(patchJson("/api/admin/users/" + userId + "/status",
                body("status", "DISABLED", "reason", "test de transaction"),
                superAdminToken).status()).isEqualTo(200);
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DISABLED);
        Integer audited = jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_log where action = 'USER_STATUS_CHANGED' "
                        + "and target_id = ? and reason = ?",
                Integer.class, Long.toString(userId), "test de transaction");
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void adminPublisherSuspensionRevokesSessionsAndPublishesAnEvent() throws Exception {
        String superAdminToken = tokenOf(login(ADMIN_EMAIL, PASSWORD));
        String slug = unique("moderated-pub");
        String ownerEmail = unique("moderated-pub-owner") + "@example.local";
        createAnonymousPublisher(slug, TenantType.INDIVIDUAL, "Moderated Publisher", ownerEmail);
        HttpResult ownerLogin = login(ownerEmail, PASSWORD);
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();

        HttpResult suspended = patchJson("/api/admin/publishers/" + tenant.getId() + "/status",
                body("status", "SUSPENDED", "reason", "contenu non conforme"), superAdminToken);
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(json(suspended).get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(tenantRepository.findBySlug(slug).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.SUSPENDED);

        assertProblem(refresh(refreshCookie(ownerLogin)), 401, "");
        assertProblem(get("/api/public/publishers/" + slug, null), 404, "");

        var events = outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId());
        JsonNode last = JSON.readTree(events.getLast().getPayload());
        assertThat(last.get("eventType").asString()).isEqualTo("PUBLISHER_UPSERTED");
        assertThat(last.get("payload").get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(auditCount("PUBLISHER_STATUS_CHANGED")).isGreaterThanOrEqualTo(1);

        HttpResult active = patchJson("/api/admin/publishers/" + tenant.getId() + "/status",
                body("status", "ACTIVE", "reason", "recours accepté"), superAdminToken);
        assertThat(active.status()).isEqualTo(200);
        assertThat(get("/api/public/publishers/" + slug, null).status()).isEqualTo(200);
    }

    @Test
    void theAuditTrailIsAppendOnlyAtTheDatabaseLevel() {
        AdminAuditLog entry = adminAuditLogRepository.saveAndFlush(new AdminAuditLog(
                null, "test@example.local", "TEST_ENTRY", "TEST", "1", null, null,
                java.time.Instant.now()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update admin_audit_log set reason = 'tampered' where id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from admin_audit_log where id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    // -------------------------------------------------------------------- bootstrap

    @Test
    void bootstrapCreatesOneGlobalSuperAdmin() throws Exception {
        List<User> admins = userRepository.findAll().stream()
                .filter(user -> user.getEmail().equals(ADMIN_EMAIL))
                .toList();
        assertThat(admins).hasSize(1);
        assertThat(admins.getFirst().getPlatformRole()).isEqualTo(PlatformRole.SUPER_ADMIN);
        Jwt adminJwt = jwtDecoder.decode(tokenOf(login(ADMIN_EMAIL, PASSWORD)));
        assertThat(adminJwt.getClaimAsString("role")).isEqualTo("SUPER_ADMIN");
        assertThat(adminJwt.getClaimAsString("tenant_id")).isNull();
        assertThat(adminJwt.getClaimAsString("tenant_slug")).isNull();
        assertThat(adminJwt.getClaimAsString("tenant_type")).isNull();
        assertThat((Boolean) adminJwt.getClaim(JwtClaims.MUST_CHANGE_PASSWORD)).isFalse();
        assertThat(managementPort).isNotEqualTo(port);
    }

    // ----------------------------------------------------------------------- helpers

    private Membership membershipOf(String tenantId, long userId) {
        return TenantContext.callAs(tenantId,
                () -> membershipRepository.findByUserId(userId).orElseThrow());
    }

    private int auditCount(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_log where action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }

    private void register(String email, String fullName) throws Exception {
        HttpResult result = post("/api/auth/register", body(
                "email", email,
                "password", PASSWORD,
                "fullName", fullName));
        assertThat(result.status()).isEqualTo(201);
    }

    private HttpResult login(String email, String password) throws Exception {
        return post("/api/auth/login", body("email", email, "password", password));
    }

    private HttpResult refresh(String cookie) throws Exception {
        return postWithCookie("/api/auth/refresh", cookie);
    }

    private HttpResult createAnonymousPublisher(String slug, TenantType type, String name,
                                                String ownerEmail) throws Exception {
        return post("/api/auth/publishers", body(
                "publisherType", type.name(),
                "publisherName", name,
                "slug", slug,
                "ownerFullName", "Owner " + slug,
                "ownerEmail", ownerEmail,
                "password", PASSWORD));
    }

    private String tokenOf(HttpResult result) throws Exception {
        assertThat(result.status()).isEqualTo(200);
        return json(result).get("accessToken").asString();
    }

    private JsonNode json(HttpResult result) throws Exception {
        return JSON.readTree(result.body());
    }

    private void assertProblem(HttpResult result, int status, String detailContains) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body()).doesNotContain("passwordHash", "password_hash");
        assertThat(result.body()).doesNotContain("\"properties\":null", "\"type\":null");
        if (!detailContains.isEmpty()) {
            assertThat(result.body()).contains(detailContains);
        }
    }

    private static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            body.put((String) keyValues[index], keyValues[index + 1]);
        }
        return body;
    }

    private HttpResult post(String path, Object body) throws Exception {
        return send(jsonRequest(path, body, null));
    }

    private HttpResult postJson(String path, Object body, String token) throws Exception {
        return send(jsonRequest(path, body, token));
    }

    private HttpResult putJson(String path, Object body, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build());
    }

    private HttpResult patchJson(String path, Object body, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body)))
                .build());
    }

    private HttpResult postWithCookie(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return send(builder.build());
    }

    private HttpResult get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult postImage(String kind, String filename, String contentType, byte[] bytes,
                                 String token) throws Exception {
        String boundary = "----sanaa" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(("Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        baseUrl() + "/api/auth/images?kind=" + kind))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpRequest jsonRequest(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body(), response.headers());
    }

    private HttpResponse<byte[]> sendBytes(URI uri) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String refreshCookie(HttpResult result) {
        String header = result.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Refresh cookie is missing: "
                        + result.headers().allValues("Set-Cookie")));
        int semicolon = header.indexOf(';');
        return semicolon < 0 ? header : header.substring(0, semicolon);
    }

    private interface IndexedCall {
        int call(int index) throws Exception;
    }

    private static List<Integer> runConcurrently(int count, IndexedCall call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int captured = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call(captured);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};
    }

    private static byte[] pngBytesWithDifferentContent() {
        byte[] bytes = pngBytes();
        bytes[bytes.length - 1] = 0x01;
        return bytes;
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("sanaa-auth-images");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record HttpResult(int status, String body, HttpHeaders headers) {
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
            Path path = Files.createTempFile("sanaa-auth-test-", ".pem");
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
