package com.sanaa.common.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves Hibernate 7.4 {@code @TenantId} behaviour against a real MySQL instance.
 *
 * <p>Test transactions are disabled and each unit of work creates its own {@link EntityManager}
 * after the tenant is set. This matches production, where the tenant must be present before the
 * Hibernate session starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class TenantIdIntegrationTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("common_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("tenant-test-schema.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // SSL is disabled only because the container generates a self-signed certificate whose
        // NotBefore can lag the host clock on Windows, which makes the driver reject the handshake.
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetTenantAndTestTable() throws SQLException {
        TenantContext.clear();
        // Test-only cleanup. A native DELETE is appropriate here because this table exists only
        // for this test and each test must start from a known state.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from tenant_test_entities");
        }
    }

    @AfterEach
    void clearTenantAfterTest() {
        TenantContext.clear();
    }

    @Test
    void findByIdUnderAnotherTenantReturnsEmpty() {
        Long id = inTransaction(TENANT_A, entityManager -> {
            TenantTestEntity entity = new TenantTestEntity("Tenant A course");
            entityManager.persist(entity);
            entityManager.flush();
            return entity.getId();
        });

        TenantTestEntity ownTenantRow = inTransaction(TENANT_A,
                entityManager -> entityManager.find(TenantTestEntity.class, id));
        assertThat(ownTenantRow).isNotNull();
        assertThat(ownTenantRow.getTenantId()).isEqualTo(TENANT_A);

        TenantTestEntity found = inTransaction(TENANT_B,
                entityManager -> entityManager.find(TenantTestEntity.class, id));

        assertThat(found).isNull();
    }

    @Test
    void queryUnderAnotherTenantReturnsNoRows() {
        inTransaction(TENANT_A, entityManager -> {
            entityManager.persist(new TenantTestEntity("Tenant A course 1"));
            entityManager.persist(new TenantTestEntity("Tenant A course 2"));
            entityManager.flush();
            return null;
        });

        List<TenantTestEntity> ownTenantRows = inTransaction(TENANT_A, entityManager ->
                entityManager.createQuery("select e from TenantTestEntity e", TenantTestEntity.class)
                        .getResultList());
        assertThat(ownTenantRows).hasSize(2);

        List<TenantTestEntity> rows = inTransaction(TENANT_B, entityManager ->
                entityManager.createQuery("select e from TenantTestEntity e", TenantTestEntity.class)
                        .getResultList());

        assertThat(rows).isEmpty();
    }

    @Test
    void updateUnderAnotherTenantAffectsNoRows() {
        Long id = inTransaction(TENANT_A, entityManager -> {
            TenantTestEntity entity = new TenantTestEntity("Original name");
            entityManager.persist(entity);
            entityManager.flush();
            return entity.getId();
        });

        int updated = inTransaction(TENANT_B, entityManager ->
                entityManager.createQuery(
                                "update TenantTestEntity e set e.name = :name where e.id = :id")
                        .setParameter("name", "Hijacked")
                        .setParameter("id", id)
                        .executeUpdate());

        assertThat(updated).isZero();

        String nameUnderTenantA = inTransaction(TENANT_A, entityManager ->
                entityManager.find(TenantTestEntity.class, id).getName());
        assertThat(nameUnderTenantA).isEqualTo("Original name");
    }

    @Test
    void withoutTenantNothingIsReadAndInsertHitsNilTenantCheck() {
        List<TenantTestEntity> rows = inTransaction(null, entityManager ->
                entityManager.createQuery("select e from TenantTestEntity e", TenantTestEntity.class)
                        .getResultList());
        assertThat(rows).isEmpty();

        assertThatThrownBy(() -> inTransaction(null, entityManager -> {
            entityManager.persist(new TenantTestEntity("No tenant"));
            entityManager.flush();
            return null;
        }))
                .isInstanceOf(PersistenceException.class)
                .satisfies(exception -> assertThat(rootCause(exception).getMessage())
                        .contains("chk_tenant_not_nil"));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private <T> T inTransaction(String tenantId, Function<EntityManager, T> work) {
        return TenantContext.callAs(tenantId, () -> {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T result = work.apply(entityManager);
                transaction.commit();
                return result;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            } finally {
                entityManager.close();
            }
        });
    }
}
