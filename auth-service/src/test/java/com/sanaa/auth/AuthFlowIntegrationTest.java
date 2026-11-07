package com.sanaa.auth;

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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.auth.bootstrap.DemoSeed;
import com.sanaa.auth.config.JwtKeyProvider;
import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP tests against a real MySQL instance. The JWT key pair is generated at test
 * startup and written to temporary files, so no private key is committed to the repository.
 *
 * <p>The demo profile is active so the demo seed can be exercised directly. The platform bootstrap
 * is disabled and the management port is random.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Testcontainers
class AuthFlowIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PASSWORD = "correct horse battery";
    private static final String COOKIE_NAME = "sanaa_refresh";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Path PRIVATE_KEY_PATH = writePem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded());
    private static final Path PUBLIC_KEY_PATH = writePem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded());

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // SSL is disabled only because the container certificate can be ahead of the host clock on
        // Windows, which makes the driver reject the handshake. The same workaround is used in
        // common's tenant integration test.
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("sanaa.jwt.private-key-path", PRIVATE_KEY_PATH::toString);
        registry.add("sanaa.jwt.public-key-path", PUBLIC_KEY_PATH::toString);
        registry.add("sanaa.bootstrap.enabled", () -> "false");
        registry.add("management.server.port", () -> "0");
        registry.add("sanaa.auth.refresh-cookie-secure", () -> "true");
        registry.add("DEMO_PASSWORD", () -> PASSWORD);
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtKeyProvider jwtKeyProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DemoSeed demoSeed;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- existing auth flow

    @Test
    void registerThenLoginIssuesTokenWithEveryClaimAndMeReturnsTheUser() throws Exception {
        Tenant tenant = createTenant("Alpha Academy", "#00B42A", "https://cdn.example.test/alpha.png");

        HttpResult register = post("/api/auth/register", Map.of(
                "tenantSlug", tenant.getSlug(),
                "email", "Alice@Example.COM",
                "password", PASSWORD,
                "fullName", "Alice Student"));
        assertThat(register.status()).isEqualTo(201);
        JsonNode registered = JSON.readTree(register.body());
        long userId = registered.get("id").asLong();
        assertThat(registered.get("email").asString()).isEqualTo("alice@example.com");
        assertThat(registered.get("fullName").asString()).isEqualTo("Alice Student");
        assertThat(registered.get("tenantSlug").asString()).isEqualTo(tenant.getSlug());
        assertThat(registered.get("role").asString()).isEqualTo("STUDENT");

        HttpResult login = login(tenant.getSlug(), "ALICE@example.com", PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        JsonNode loginBody = JSON.readTree(login.body());
        String token = loginBody.get("accessToken").asString();
        assertThat(loginBody.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(loginBody.size()).isEqualTo(2);
        assertThat(token).isNotBlank();

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenant.getId());
        assertThat(jwt.getClaimAsString("tenant_slug")).isEqualTo(tenant.getSlug());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Alice Student");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("sanaa-auth");
        assertThat(jwt.getAudience()).containsExactly("sanaa-api");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isEqualTo(Duration.ofMinutes(15).getSeconds());

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(jwtKeyProvider.keyId());

        HttpResult me = get("/api/auth/me", token);
        assertThat(me.status()).isEqualTo(200);
        JsonNode meBody = JSON.readTree(me.body());
        assertThat(meBody.get("id").asLong()).isEqualTo(userId);
        assertThat(meBody.get("email").asString()).isEqualTo("alice@example.com");
        assertThat(meBody.get("fullName").asString()).isEqualTo("Alice Student");
        assertThat(meBody.get("role").asString()).isEqualTo("STUDENT");
        assertThat(meBody.get("tenantId").asString()).isEqualTo(tenant.getId());
        assertThat(meBody.get("tenantSlug").asString()).isEqualTo(tenant.getSlug());

        assertThat(get("/api/auth/me", null).status()).isEqualTo(401);
    }

    @Test
    void jwksContainsOnlyThePublicKeyAndVerifiesAFreshToken() throws Exception {
        Tenant tenant = createTenant("JWKS Academy", "#165DFF", null);
        register(tenant.getSlug(), "jwks@example.com", "JWKS Student");
        HttpResult login = login(tenant.getSlug(), "jwks@example.com", PASSWORD);
        String token = JSON.readTree(login.body()).get("accessToken").asString();

        HttpResult jwks = get("/.well-known/jwks.json", null);
        assertThat(jwks.status()).isEqualTo(200);
        JsonNode body = JSON.readTree(jwks.body());
        assertThat(body.get("keys").size()).isEqualTo(1);

        JsonNode keyNode = body.get("keys").get(0);
        assertThat(keyNode.get("kty").asString()).isEqualTo("RSA");
        assertThat(keyNode.get("alg").asString()).isEqualTo("RS256");
        assertThat(keyNode.get("d")).isNull();
        assertThat(keyNode.get("p")).isNull();
        assertThat(keyNode.get("q")).isNull();

        JWK jwk = JWK.parse(keyNode.toString());
        RSAKey rsaKey = (RSAKey) jwk;
        String expectedKid = new RSAKey.Builder(rsaKey.toRSAPublicKey()).build()
                .computeThumbprint().toString();
        assertThat(keyNode.get("kid").asString()).isEqualTo(expectedKid);
        assertThat(keyNode.get("kid").asString()).isEqualTo(jwtKeyProvider.keyId());

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();
    }

    @Test
    void sameEmailCanRegisterInTwoTenantsAndProducesSeparateUsers() throws Exception {
        Tenant tenantA = createTenant("Tenant A", "#165DFF", null);
        Tenant tenantB = createTenant("Tenant B", "#722ED1", null);

        HttpResult first = post("/api/auth/register", Map.of(
                "tenantSlug", tenantA.getSlug(),
                "email", "shared@example.com",
                "password", PASSWORD,
                "fullName", "Shared User"));
        HttpResult second = post("/api/auth/register", Map.of(
                "tenantSlug", tenantB.getSlug(),
                "email", "shared@example.com",
                "password", PASSWORD,
                "fullName", "Shared User"));
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);

        long firstId = JSON.readTree(first.body()).get("id").asLong();
        long secondId = JSON.readTree(second.body()).get("id").asLong();
        assertThat(firstId).isNotEqualTo(secondId);

        Jwt tokenA = jwtDecoder.decode(JSON.readTree(login(tenantA.getSlug(), "shared@example.com",
                PASSWORD).body()).get("accessToken").asString());
        Jwt tokenB = jwtDecoder.decode(JSON.readTree(login(tenantB.getSlug(), "shared@example.com",
                PASSWORD).body()).get("accessToken").asString());
        assertThat(tokenA.getClaimAsString("tenant_id")).isEqualTo(tenantA.getId());
        assertThat(tokenB.getClaimAsString("tenant_id")).isEqualTo(tenantB.getId());
        assertThat(tokenA.getSubject()).isNotEqualTo(tokenB.getSubject());
    }

    @Test
    void loginWithAnotherTenantSlugFailsWithTheGenericError() throws Exception {
        Tenant tenantA = createTenant("Tenant A Login", "#165DFF", null);
        Tenant tenantB = createTenant("Tenant B Login", "#722ED1", null);
        register(tenantA.getSlug(), "cross@example.com", "Cross Tenant");

        HttpResult response = login(tenantB.getSlug(), "cross@example.com", PASSWORD);
        assertThat(response.status()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).get("detail").asString())
                .isEqualTo("Invalid tenant, email or password.");
    }

    @Test
    void everyCredentialFailureReturnsTheSameStatusAndBody() throws Exception {
        Tenant activeTenant = createTenant("Active Tenant", "#165DFF", null);
        register(activeTenant.getSlug(), "known@example.com", "Known User");

        HttpResult wrongPassword = login(activeTenant.getSlug(), "known@example.com", "wrong password");
        HttpResult unknownEmail = login(activeTenant.getSlug(), "missing@example.com", PASSWORD);
        HttpResult unknownTenant = login("does-not-exist-" + shortId(), "known@example.com", PASSWORD);

        Tenant disabledUserTenant = createTenant("Disabled User Tenant", "#165DFF", null);
        register(disabledUserTenant.getSlug(), "disabled@example.com", "Disabled User");
        disableUser(disabledUserTenant.getId(), "disabled@example.com");
        HttpResult disabledUser = login(disabledUserTenant.getSlug(), "disabled@example.com", PASSWORD);

        Tenant suspendedTenant = createTenant("Suspended Tenant", "#165DFF", null);
        register(suspendedTenant.getSlug(), "suspended@example.com", "Suspended User");
        suspendTenant(suspendedTenant);
        HttpResult suspended = login(suspendedTenant.getSlug(), "suspended@example.com", PASSWORD);

        List<HttpResult> failures = List.of(wrongPassword, unknownEmail, unknownTenant, disabledUser,
                suspended);
        assertThat(failures).allSatisfy(result -> assertThat(result.status()).isEqualTo(401));
        String expectedBody = failures.getFirst().body();
        assertThat(failures).allSatisfy(result -> assertThat(result.body()).isEqualTo(expectedBody));
    }

    @Test
    void roleInRegistrationPayloadIsIgnored() throws Exception {
        Tenant tenant = createTenant("Role Test", "#165DFF", null);

        HttpResult register = post("/api/auth/register", Map.of(
                "tenantSlug", tenant.getSlug(),
                "email", "role@example.com",
                "password", PASSWORD,
                "fullName", "Role Tester",
                "role", "SUPER_ADMIN"));
        assertThat(register.status()).isEqualTo(201);
        assertThat(JSON.readTree(register.body()).get("role").asString()).isEqualTo("STUDENT");

        Jwt jwt = jwtDecoder.decode(JSON.readTree(login(tenant.getSlug(), "role@example.com",
                PASSWORD).body()).get("accessToken").asString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
    }

    @Test
    void duplicateEmailInTheSameTenantReturnsConflict() throws Exception {
        Tenant tenant = createTenant("Duplicate Test", "#165DFF", null);
        register(tenant.getSlug(), "duplicate@example.com", "First User");

        HttpResult duplicate = post("/api/auth/register", Map.of(
                "tenantSlug", tenant.getSlug(),
                "email", "DUPLICATE@example.com",
                "password", PASSWORD,
                "fullName", "Second User"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).get("title").asString()).isEqualTo("Conflict");
    }

    @Test
    void brandingIsPublicAndHiddenForUnknownOrSuspendedTenants() throws Exception {
        Tenant tenant = createTenant("Branded Academy", "#00B42A", "https://cdn.example.test/logo.png");

        HttpResult branding = get("/api/auth/tenants/" + tenant.getSlug() + "/branding", null);
        assertThat(branding.status()).isEqualTo(200);
        JsonNode body = JSON.readTree(branding.body());
        assertThat(body.get("name").asString()).isEqualTo("Branded Academy");
        assertThat(body.get("primaryColor").asString()).isEqualTo("#00B42A");
        assertThat(body.get("logoUrl").asString()).isEqualTo("https://cdn.example.test/logo.png");
        assertThat(body.get("status")).isNull();
        assertThat(body.get("id")).isNull();

        suspendTenant(tenant);
        assertThat(get("/api/auth/tenants/" + tenant.getSlug() + "/branding", null).status())
                .isEqualTo(404);
        assertThat(get("/api/auth/tenants/unknown-" + shortId() + "/branding", null).status())
                .isEqualTo(404);
    }

    @Test
    void shortPasswordIsRejectedWithValidationErrors() throws Exception {
        Tenant tenant = createTenant("Validation Test", "#165DFF", null);

        HttpResult response = post("/api/auth/register", Map.of(
                "tenantSlug", tenant.getSlug(),
                "email", "short@example.com",
                "password", "short",
                "fullName", "Short Password"));
        assertThat(response.status()).isEqualTo(400);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("title").asString()).isEqualTo("Bad Request");
        assertThat(body.get("errors").get("password")).isNotNull();
    }

    // ---------------------------------------------------------------- task A

    @Test
    void reservedTenantSlugsAreRejectedLikeUnknownTenants() throws Exception {
        // Create the platform tenant directly, proving that registration still cannot join it even
        // though the slug exists and is ACTIVE.
        tenantRepository.findBySlug("platform").orElseGet(() -> tenantRepository.saveAndFlush(
                new Tenant(UUID.randomUUID().toString(), "platform", "Platform", TenantStatus.ACTIVE,
                        "#165DFF", null)));

        String expectedBody = post("/api/auth/register", Map.of(
                "tenantSlug", "unknown-" + shortId(),
                "email", "reserved-" + shortId() + "@example.com",
                "password", PASSWORD,
                "fullName", "Reserved User")).body();

        for (String slug : List.of("platform", "www", "api", "admin", "auth", "app", "static", "mail")) {
            HttpResult response = post("/api/auth/register", Map.of(
                    "tenantSlug", slug,
                    "email", "reserved-" + slug + "-" + shortId() + "@example.com",
                    "password", PASSWORD,
                    "fullName", "Reserved User"));
            assertThat(response.status()).as("slug %s", slug).isEqualTo(404);
            assertThat(response.body()).as("slug %s", slug).isEqualTo(expectedBody);
        }
    }

    @Test
    void concurrentDuplicateRegistrationReturnsOneCreatedAndOneConflict() throws Exception {
        Tenant tenant = createTenant("Concurrent Registration", "#165DFF", null);
        String email = "race-" + shortId() + "@example.com";
        HttpRequest request = jsonRequest("/api/auth/register", Map.of(
                "tenantSlug", tenant.getSlug(),
                "email", email,
                "password", PASSWORD,
                "fullName", "Race User"));

        CompletableFuture<HttpResponse<String>> first =
                HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second =
                HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();

        List<Integer> statuses = List.of(first.join().statusCode(), second.join().statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void prometheusIsExposedOnTheManagementPortOnly() throws Exception {
        Tenant tenant = createTenant("Metrics Academy", "#165DFF", null);
        register(tenant.getSlug(), "metrics@example.com", "Metrics Student");
        login(tenant.getSlug(), "metrics@example.com", PASSWORD);

        HttpResponse<String> health = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP");

        HttpResponse<String> info = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + "/actuator/info")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(info.statusCode()).isEqualTo(200);

        HttpResponse<String> prometheus = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("application=\"auth-service\"");
        assertThat(prometheus.body()).doesNotContain(tenant.getId());

        HttpResponse<String> mainPortPrometheus = HTTP.send(HttpRequest.newBuilder(
                URI.create(baseUrl() + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(mainPortPrometheus.statusCode()).isEqualTo(404);
    }

    // ---------------------------------------------------------------- refresh and logout

    @Test
    void loginSetsRefreshCookieWithTheRequiredFlags() throws Exception {
        Tenant tenant = createTenant("Cookie Flags", "#165DFF", null);
        register(tenant.getSlug(), "cookie@example.com", "Cookie User");

        HttpResult login = login(tenant.getSlug(), "cookie@example.com", PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(JSON.readTree(login.body()).size()).isEqualTo(2);

        String header = setCookie(login);
        assertThat(header).startsWith(COOKIE_NAME + "=");
        assertThat(header).contains("Path=/api/auth");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Strict");
        assertThat(header).contains("Secure");
        assertThat(header).contains("Max-Age=604800");
        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void refreshRotatesTokenAndRejectsTheOldToken() throws Exception {
        Tenant tenant = createTenant("Rotation", "#165DFF", null);
        register(tenant.getSlug(), "rotation@example.com", "Rotation User");

        String firstCookie = refreshCookie(login(tenant.getSlug(), "rotation@example.com", PASSWORD));
        HttpResult refreshed = refresh(firstCookie);
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(JSON.readTree(refreshed.body()).get("accessToken").asString()).isNotBlank();
        assertThat(JSON.readTree(refreshed.body()).size()).isEqualTo(2);

        String secondCookie = refreshCookie(refreshed);
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        HttpResult oldToken = refresh(firstCookie);
        assertThat(oldToken.status()).isEqualTo(401);
    }

    @Test
    void reuseOfRotatedTokenRevokesTheNewTokenToo() throws Exception {
        Tenant tenant = createTenant("Reuse Detection", "#165DFF", null);
        register(tenant.getSlug(), "reuse@example.com", "Reuse User");

        String firstCookie = refreshCookie(login(tenant.getSlug(), "reuse@example.com", PASSWORD));
        HttpResult refreshed = refresh(firstCookie);
        assertThat(refreshed.status()).isEqualTo(200);
        String secondCookie = refreshCookie(refreshed);

        assertThat(refresh(firstCookie).status()).isEqualTo(401);
        assertThat(refresh(secondCookie).status()).isEqualTo(401);
    }

    @Test
    void concurrentRefreshAllowsExactlyOneSuccess() throws Exception {
        Tenant tenant = createTenant("Concurrent Refresh", "#165DFF", null);
        register(tenant.getSlug(), "parallel@example.com", "Parallel User");
        String cookie = refreshCookie(login(tenant.getSlug(), "parallel@example.com", PASSWORD));

        HttpRequest request = cookieRequest("/api/auth/refresh", cookie);
        CompletableFuture<HttpResponse<String>> first =
                HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second =
                HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).join();

        List<Integer> statuses = List.of(first.join().statusCode(), second.join().statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Tenant tenant = createTenant("Expired Refresh", "#165DFF", null);
        register(tenant.getSlug(), "expired@example.com", "Expired User");
        String accessToken = JSON.readTree(login(tenant.getSlug(), "expired@example.com", PASSWORD)
                .body()).get("accessToken").asString();
        Jwt jwt = jwtDecoder.decode(accessToken);

        String rawToken = "expired-" + UUID.randomUUID();
        TenantContext.runAs(tenant.getId(), () -> refreshTokenRepository.saveAndFlush(
                new RefreshToken(tenant.getId(), Long.parseLong(jwt.getSubject()),
                        hash(rawToken), Instant.now().minusSeconds(60))));

        HttpResult expired = refresh(COOKIE_NAME + "=" + rawToken);
        HttpResult unknown = refresh(COOKIE_NAME + "=unknown-" + UUID.randomUUID());
        HttpResult missing = refresh(null);

        assertThat(List.of(expired, unknown, missing))
                .allSatisfy(result -> assertThat(result.status()).isEqualTo(401));
        assertThat(unknown.body()).isEqualTo(expired.body());
        assertThat(missing.body()).isEqualTo(expired.body());
    }

    @Test
    void suspendedTenantCannotRefresh() throws Exception {
        Tenant tenant = createTenant("Suspended Refresh", "#165DFF", null);
        register(tenant.getSlug(), "suspended-refresh@example.com", "Suspended Refresh");
        String cookie = refreshCookie(login(tenant.getSlug(), "suspended-refresh@example.com", PASSWORD));

        // Direct suspension does not revoke tokens, so this exercises the tenant check in rotation.
        suspendTenant(tenant);
        assertThat(refresh(cookie).status()).isEqualTo(401);
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        Tenant tenant = createTenant("Disabled Refresh", "#165DFF", null);
        register(tenant.getSlug(), "disabled-refresh@example.com", "Disabled Refresh");
        String cookie = refreshCookie(login(tenant.getSlug(), "disabled-refresh@example.com", PASSWORD));

        disableUser(tenant.getId(), "disabled-refresh@example.com");
        assertThat(refresh(cookie).status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheTokenAndAlwaysReturnsNoContent() throws Exception {
        Tenant tenant = createTenant("Logout", "#165DFF", null);
        register(tenant.getSlug(), "logout@example.com", "Logout User");
        String cookie = refreshCookie(login(tenant.getSlug(), "logout@example.com", PASSWORD));

        HttpResult logout = postWithCookie("/api/auth/logout", cookie);
        assertThat(logout.status()).isEqualTo(204);
        String cleared = setCookie(logout);
        assertThat(cleared).startsWith(COOKIE_NAME + "=");
        assertThat(cleared).contains("Max-Age=0");
        assertThat(cleared).contains("Path=/api/auth");
        assertThat(cleared).contains("HttpOnly");
        assertThat(cleared).contains("SameSite=Strict");
        assertThat(cleared).contains("Secure");

        assertThat(refresh(cookie).status()).isEqualTo(401);
        assertThat(postWithCookie("/api/auth/logout", null).status()).isEqualTo(204);
    }

    // ---------------------------------------------------------------- admin users

    @Test
    void tenantAdminCreatesInstructorInOwnTenant() throws Exception {
        String adminToken = loginDemoToken("bhihi", "admin@bhihi.local");
        String email = "instructor-" + shortId() + "@bhihi.local";

        HttpResult created = postJson("/api/auth/admin/users", Map.of(
                "email", email,
                "password", PASSWORD,
                "fullName", "New Instructor",
                "role", "INSTRUCTOR"), adminToken);
        assertThat(created.status()).isEqualTo(201);
        assertThat(JSON.readTree(created.body()).get("role").asString()).isEqualTo("INSTRUCTOR");
        assertThat(JSON.readTree(created.body()).get("tenantId").asString())
                .isEqualTo(tenant("bhihi").getId());

        HttpResult login = login("bhihi", email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(jwtDecoder.decode(JSON.readTree(login.body()).get("accessToken").asString())
                .getClaimAsString("role")).isEqualTo("INSTRUCTOR");
    }

    @Test
    void tenantAdminCannotCreateUserInAnotherTenant() throws Exception {
        Tenant acme = tenant("acme");
        String adminToken = loginDemoToken("bhihi", "admin@bhihi.local");
        String email = "foreign-" + shortId() + "@example.com";

        HttpResult created = postJson("/api/auth/admin/users", Map.of(
                "tenantId", acme.getId(),
                "email", email,
                "password", PASSWORD,
                "fullName", "Foreign Attempt",
                "role", "INSTRUCTOR"), adminToken);
        assertThat(created.status()).isEqualTo(201);
        assertThat(JSON.readTree(created.body()).get("tenantId").asString())
                .isEqualTo(tenant("bhihi").getId());

        assertThat(login("bhihi", email, PASSWORD).status()).isEqualTo(200);
        assertThat(login("acme", email, PASSWORD).status()).isEqualTo(401);
    }

    @Test
    void tenantAdminDuplicateEmailReturnsConflict() throws Exception {
        String adminToken = loginDemoToken("bhihi", "admin@bhihi.local");
        String email = "duplicate-admin-" + shortId() + "@bhihi.local";
        Map<String, Object> request = Map.of(
                "email", email,
                "password", PASSWORD,
                "fullName", "Duplicate Admin User",
                "role", "INSTRUCTOR");

        assertThat(postJson("/api/auth/admin/users", request, adminToken).status()).isEqualTo(201);
        HttpResult duplicate = postJson("/api/auth/admin/users", request, adminToken);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).get("title").asString()).isEqualTo("Conflict");
    }

    @Test
    void studentCannotCreateTenantUsers() throws Exception {
        String studentToken = loginDemoToken("bhihi", "student1@bhihi.local");

        HttpResult response = postJson("/api/auth/admin/users", Map.of(
                "email", "forbidden-" + shortId() + "@example.com",
                "password", PASSWORD,
                "fullName", "Forbidden User",
                "role", "INSTRUCTOR"), studentToken);
        assertThat(response.status()).isEqualTo(403);
        assertThat(JSON.readTree(response.body()).get("title").asString()).isEqualTo("Forbidden");
    }

    @Test
    void superAdminCannotUseTheTenantAdminEndpoint() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();

        HttpResult response = postJson("/api/auth/admin/users", Map.of(
                "email", "super-created-" + shortId() + "@example.com",
                "password", PASSWORD,
                "fullName", "Super Created",
                "role", "INSTRUCTOR"), superAdmin.token());
        assertThat(response.status()).isEqualTo(403);
    }

    // ---------------------------------------------------------------- platform

    @Test
    void superAdminCreatesTenantAndItsFirstAdmin() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();
        String slug = "created-" + shortId();
        String adminEmail = "admin@" + slug + ".local";

        HttpResult created = postJson("/api/platform/tenants", Map.of(
                "slug", slug,
                "name", "Created Academy",
                "adminEmail", adminEmail,
                "adminPassword", PASSWORD,
                "adminFullName", "Created Admin"), superAdmin.token());
        assertThat(created.status()).isEqualTo(201);
        JsonNode body = JSON.readTree(created.body());
        assertThat(body.get("slug").asString()).isEqualTo(slug);
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("id").asString()).isNotBlank();

        HttpResult login = login(slug, adminEmail, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(jwtDecoder.decode(JSON.readTree(login.body()).get("accessToken").asString())
                .getClaimAsString("role")).isEqualTo("TENANT_ADMIN");
    }

    @Test
    void invalidReservedAndDuplicateTenantSlugsAreRejected() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();

        assertThat(postJson("/api/platform/tenants", Map.of(
                "slug", "ab", "name", "Short", "adminEmail", "short@example.com",
                "adminPassword", PASSWORD, "adminFullName", "Short Admin"),
                superAdmin.token()).status()).isEqualTo(400);

        assertThat(postJson("/api/platform/tenants", Map.of(
                "slug", "api", "name", "Reserved", "adminEmail", "reserved@example.com",
                "adminPassword", PASSWORD, "adminFullName", "Reserved Admin"),
                superAdmin.token()).status()).isEqualTo(400);

        String slug = "duplicate-" + shortId();
        Map<String, Object> request = Map.of(
                "slug", slug, "name", "Duplicate", "adminEmail", "admin@" + slug + ".local",
                "adminPassword", PASSWORD, "adminFullName", "Duplicate Admin");
        assertThat(postJson("/api/platform/tenants", request, superAdmin.token()).status())
                .isEqualTo(201);
        assertThat(postJson("/api/platform/tenants", request, superAdmin.token()).status())
                .isEqualTo(409);
    }

    @Test
    void platformListIsPaginatedAndProtectedBySuperAdmin() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();
        String slug = "listed-" + shortId();
        postJson("/api/platform/tenants", Map.of(
                "slug", slug, "name", "Listed Academy",
                "adminEmail", "admin@" + slug + ".local",
                "adminPassword", PASSWORD, "adminFullName", "Listed Admin"), superAdmin.token());

        HttpResult list = get("/api/platform/tenants?page=0&size=5", superAdmin.token());
        assertThat(list.status()).isEqualTo(200);
        JsonNode body = JSON.readTree(list.body());
        assertThat(body.get("size").asInt()).isEqualTo(5);
        assertThat(body.get("items").isArray()).isTrue();
        assertThat(body.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(body.toString()).contains(slug);

        String tenantAdminToken = loginDemoToken("bhihi", "admin@bhihi.local");
        String studentToken = loginDemoToken("bhihi", "student1@bhihi.local");
        assertThat(get("/api/platform/tenants", tenantAdminToken).status()).isEqualTo(403);
        assertThat(get("/api/platform/tenants", studentToken).status()).isEqualTo(403);
    }

    @Test
    void suspendingTenantBlocksLoginAndRefreshAndRevokesSessions() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();
        String slug = "suspended-" + shortId();
        String adminEmail = "admin@" + slug + ".local";
        HttpResult created = postJson("/api/platform/tenants", Map.of(
                "slug", slug, "name", "Suspended Academy",
                "adminEmail", adminEmail,
                "adminPassword", PASSWORD, "adminFullName", "Suspended Admin"),
                superAdmin.token());
        String tenantId = JSON.readTree(created.body()).get("id").asString();
        String cookie = refreshCookie(login(slug, adminEmail, PASSWORD));

        HttpResult suspended = patchJson("/api/platform/tenants/" + tenantId + "/status",
                Map.of("status", "SUSPENDED"), superAdmin.token());
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(JSON.readTree(suspended.body()).get("status").asString()).isEqualTo("SUSPENDED");

        assertThat(login(slug, adminEmail, PASSWORD).status()).isEqualTo(401);
        assertThat(refresh(cookie).status()).isEqualTo(401);
    }

    @Test
    void platformTenantCannotBeSuspended() throws Exception {
        SuperAdmin superAdmin = createSuperAdmin();
        Tenant platform = tenantRepository.findBySlug("platform").orElseGet(() ->
                tenantRepository.saveAndFlush(new Tenant(UUID.randomUUID().toString(), "platform",
                        "Platform", TenantStatus.ACTIVE, "#165DFF", null)));

        HttpResult response = patchJson("/api/platform/tenants/" + platform.getId() + "/status",
                Map.of("status", "SUSPENDED"), superAdmin.token());
        assertThat(response.status()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).get("detail").asString())
                .contains("platform");
    }

    // ---------------------------------------------------------------- demo seed

    @Test
    void demoSeedCreatesTheExpectedAccountsAndIsIdempotent() {
        Tenant bhihi = tenant("bhihi");
        Tenant acme = tenant("acme");
        assertThat(bhihi.getPrimaryColor()).isNotEqualTo(acme.getPrimaryColor());
        assertDemoAccounts(bhihi);
        assertDemoAccounts(acme);

        long usersBefore = countUsers(bhihi.getId());
        demoSeed.run(null);
        demoSeed.run(null);
        assertThat(countUsers(bhihi.getId())).isEqualTo(usersBefore);
    }

    // ---------------------------------------------------------------- helpers

    private void assertDemoAccounts(Tenant tenant) {
        TenantContext.runAs(tenant.getId(), () -> {
            assertRole(tenant.getSlug(), "admin", Role.TENANT_ADMIN);
            assertRole(tenant.getSlug(), "instructor", Role.INSTRUCTOR);
            assertRole(tenant.getSlug(), "student1", Role.STUDENT);
            assertRole(tenant.getSlug(), "student2", Role.STUDENT);
        });
    }

    private void assertRole(String slug, String localPart, Role role) {
        User user = userRepository.findByEmail(localPart + "@" + slug + ".local").orElseThrow();
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private long countUsers(String tenantId) {
        return TenantContext.callAs(tenantId, userRepository::count);
    }

    private Tenant tenant(String slug) {
        return tenantRepository.findBySlug(slug).orElseThrow();
    }

    private String loginDemoToken(String tenantSlug, String email) throws Exception {
        HttpResult login = login(tenantSlug, email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        return JSON.readTree(login.body()).get("accessToken").asString();
    }

    private SuperAdmin createSuperAdmin() {
        Tenant tenant = createTenant("Super Admin Tenant", "#165DFF", null);
        String email = "super-" + shortId() + "@example.com";
        TenantContext.runAs(tenant.getId(), () -> userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode(PASSWORD), "Super Admin", Role.SUPER_ADMIN)));
        try {
            HttpResult login = login(tenant.getSlug(), email, PASSWORD);
            assertThat(login.status()).isEqualTo(200);
            return new SuperAdmin(JSON.readTree(login.body()).get("accessToken").asString(),
                    tenant.getId());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Tenant createTenant(String name, String primaryColor, String logoUrl) {
        String slug = "t" + shortId();
        Tenant tenant = new Tenant(UUID.randomUUID().toString(), slug, name, TenantStatus.ACTIVE,
                primaryColor, logoUrl);
        return tenantRepository.saveAndFlush(tenant);
    }

    private void suspendTenant(Tenant tenant) {
        tenant.suspend();
        tenantRepository.saveAndFlush(tenant);
    }

    private void disableUser(String tenantId, String email) {
        TenantContext.runAs(tenantId, () -> {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.changeStatus(UserStatus.DISABLED);
            userRepository.saveAndFlush(user);
        });
    }

    private void register(String tenantSlug, String email, String fullName) throws Exception {
        HttpResult response = post("/api/auth/register", Map.of(
                "tenantSlug", tenantSlug,
                "email", email,
                "password", PASSWORD,
                "fullName", fullName));
        assertThat(response.status()).isEqualTo(201);
    }

    private HttpResult login(String tenantSlug, String email, String password) throws Exception {
        return post("/api/auth/login", Map.of(
                "tenantSlug", tenantSlug,
                "email", email,
                "password", password));
    }

    private HttpResult refresh(String cookie) throws Exception {
        return postWithCookie("/api/auth/refresh", cookie);
    }

    private HttpResult post(String path, Object body) throws Exception {
        return send(jsonRequest(path, body));
    }

    private HttpResult postJson(String path, Object body, String bearerToken) throws Exception {
        return send(jsonRequest(path, body, bearerToken));
    }

    private HttpResult patchJson(String path, Object body, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return send(request);
    }

    private HttpResult postWithCookie(String path, String cookie) throws Exception {
        return send(cookieRequest(path, cookie));
    }

    private HttpResult get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return send(builder.build());
    }

    private HttpRequest jsonRequest(String path, Object body) throws Exception {
        return jsonRequest(path, body, null);
    }

    private HttpRequest jsonRequest(String path, Object body, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return builder.build();
    }

    private HttpRequest cookieRequest(String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private HttpResponse<String> sendRaw(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response = sendRaw(request);
        return new HttpResult(response.statusCode(), response.body(), response.headers());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String setCookie(HttpResult result) {
        return result.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Refresh cookie is missing: "
                        + result.headers().allValues("Set-Cookie")));
    }

    private static String refreshCookie(HttpResult result) {
        String header = setCookie(result);
        int semicolon = header.indexOf(';');
        return semicolon < 0 ? header : header.substring(0, semicolon);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record HttpResult(int status, String body, HttpHeaders headers) {
    }

    private record SuperAdmin(String token, String tenantId) {
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
            String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
            Path path = Files.createTempFile("sanaa-auth-test-", ".pem");
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
