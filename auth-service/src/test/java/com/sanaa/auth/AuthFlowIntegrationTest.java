package com.sanaa.auth;

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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.auth.config.JwtKeyProvider;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP tests against a real MySQL instance. The JWT key pair is generated at test
 * startup and written to temporary files, so no private key is committed to the repository.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String PASSWORD = "correct horse battery";
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
        registry.add("management.server.port", () -> "-1");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtKeyProvider jwtKeyProvider;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

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

    private HttpResult post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private HttpResult get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = HTTP.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private record HttpResult(int status, String body) {
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
