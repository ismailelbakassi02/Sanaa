package com.sanaa.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.OutboxEvent;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.auth.outbox.OutboxEventRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.common.event.EventType;
import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo seed must publish the publishers it creates, otherwise the public catalogue has no
 * publisher snapshot and shows an empty publisher name.
 *
 * <p>These tests run the seed methods directly, twice, because the defect is about creation: the
 * first run emits, a rerun must not, and both the row and the event must roll back together.
 */
@SpringBootTest
@Testcontainers
class DemoSeedOutboxTest {

    private static final String PASSWORD = "un mot de passe de test";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Path PRIVATE_KEY_PATH = writePem("PRIVATE KEY",
            KEY_PAIR.getPrivate().getEncoded());
    private static final Path PUBLIC_KEY_PATH = writePem("PUBLIC KEY",
            KEY_PAIR.getPublic().getEncoded());

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            // The audit migration creates triggers, which need this flag on a binary-logged server.
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("auth_seed_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("sanaa.jwt.private-key-path", PRIVATE_KEY_PATH::toString);
        registry.add("sanaa.jwt.public-key-path", PUBLIC_KEY_PATH::toString);
        registry.add("sanaa.outbox.poller.enabled", () -> "false");
        // No platform admin is needed: the test only exercises the demo seed methods.
        registry.add("sanaa.bootstrap.enabled", () -> "false");
        // No broker is needed here; the closed port keeps the admin client from hanging.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("spring.kafka.admin.properties.default.api.timeout.ms", () -> "1000");
        registry.add("spring.kafka.admin.properties.request.timeout.ms", () -> "1000");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    @Autowired
    private DemoSeedService demoSeedService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Holds the tenant created inside the rolled-back transaction. */
    private final AtomicReference<Tenant> detail = new AtomicReference<>();

    @Test
    void eventsAreWrittenOnlyWhenTheSeedCreatesTheTenantAndItsInstructors() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "seed-" + suffix;

        Tenant tenant = createTenant(slug, suffix);
        createAccounts(tenant.getId(), suffix);

        List<OutboxEvent> events = outboxEventRepository
                .findAllByAggregateIdOrderByIdAsc(tenant.getId());
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(EventType.PUBLISHER_UPSERTED, EventType.INSTRUCTOR_UPDATED);
        assertThat(events).allSatisfy(event ->
                assertThat(event.getTopic()).isEqualTo("sanaa.publishers.v1"));

        JsonNode publisher = JSON.readTree(events.get(0).getPayload());
        assertThat(publisher.get("payload").get("slug").asString()).isEqualTo(slug);
        assertThat(publisher.get("payload").get("name").asString())
                .isEqualTo("Éditeur " + suffix);
        assertThat(publisher.get("payload").get("status").asString()).isEqualTo("ACTIVE");

        // The tenant admin is not an instructor, so only the instructor has a snapshot event.
        JsonNode instructor = JSON.readTree(events.get(1).getPayload());
        assertThat(instructor.get("payload").get("tenantId").asString()).isEqualTo(tenant.getId());
        assertThat(instructor.get("payload").get("name").asString())
                .isEqualTo("Formateur " + suffix);

        // A rerun finds the tenant, the users and the memberships and must emit nothing.
        createTenant(slug, suffix);
        createAccounts(tenant.getId(), suffix);
        assertThat(outboxEventRepository.findAllByAggregateIdOrderByIdAsc(tenant.getId()))
                .hasSize(2);
        assertThat(tenantRepository.findBySlug(slug)).isPresent();
    }

    @Test
    void thePublisherEventRollsBackWithItsTenant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "rollback-" + suffix;

        new TransactionTemplate(transactionManager).execute(status -> {
            detail.set(createTenant(slug, suffix));
            // The seed transaction is rolled back: neither the row nor the event may remain.
            status.setRollbackOnly();
            return null;
        });

        assertThat(detail.get()).isNotNull();
        assertThat(tenantRepository.findBySlug(slug)).isEmpty();
        assertThat(outboxEventRepository.findAllByAggregateIdOrderByIdAsc(detail.get().getId()))
                .isEmpty();
    }

    @Test
    void theInstructorEventRollsBackWithItsMembership() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = createTenant("rollback-members-" + suffix, suffix);

        // The tenant context must be set before the transaction starts: Hibernate resolves the
        // tenant once, when the session opens.
        TenantContext.callAs(tenant.getId(), () -> {
            new TransactionTemplate(transactionManager).execute(status -> {
                createAccounts(tenant.getId(), suffix);
                status.setRollbackOnly();
                return null;
            });
            return null;
        });

        // Only the publisher event of the committed tenant remains.
        List<OutboxEvent> events = outboxEventRepository
                .findAllByAggregateIdOrderByIdAsc(tenant.getId());
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(EventType.PUBLISHER_UPSERTED);
        assertThat(userRepository.findByEmail("seed-prof-" + suffix + "@example.local")).isEmpty();
    }

    private Tenant createTenant(String slug, String suffix) {
        return demoSeedService.ensureTenant(slug, "Éditeur " + suffix, TenantType.ORGANIZATION,
                "#00B42A", "Titre de démonstration", "Description de démonstration",
                "contact-" + suffix + "@example.local");
    }

    private void createAccounts(String tenantId, String suffix) {
        TenantContext.runAs(tenantId, () -> demoSeedService.ensurePublisherAccounts(tenantId,
                List.of(
                        new DemoSeedService.SeedAccount("seed-admin-" + suffix + "@example.local",
                                "Admin " + suffix, MembershipRole.TENANT_ADMIN,
                                "Responsable pédagogique", "Bio de démonstration", null, null),
                        new DemoSeedService.SeedAccount("seed-prof-" + suffix + "@example.local",
                                "Formateur " + suffix, MembershipRole.INSTRUCTOR,
                                "Formateur", "Bio de démonstration", null, null)),
                PASSWORD));
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
            Path path = Files.createTempFile("sanaa-auth-seed-test-", ".pem");
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
