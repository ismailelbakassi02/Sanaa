package com.sanaa.auth.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that V2 transforms a populated V1 database, not just an empty one.
 *
 * <p>The test migrates to V1, inserts the shapes that existed before the publisher model (a platform
 * tenant with its SUPER_ADMIN, an organization, an individual publisher, duplicate emails across
 * tenants and a tenant refresh token), then migrates to V2 and checks the resulting rows. This is the
 * only test that guards the data migration; the integration tests always start from the final schema.
 */
@Testcontainers
class MigrationV1ToV2Test {

    private static final String PLATFORM_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ATLAS_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String ACME_ID = "00000000-0000-0000-0000-0000000000ac";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            // V2 creates the append-only audit triggers. MySQL enables binary logging by default
            // and a non-SUPER application user may not create triggers unless this flag is set.
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("auth_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void transformsTheV1IdentityModelIntoGlobalUsersAndMemberships() throws Exception {
        resetDatabase();
        migrateTo(MigrationVersion.fromVersion("1"));
        seedV1();

        migrateTo(MigrationVersion.fromVersion("2"));

        try (Connection connection = connection()) {
            assertThat(queryStrings(connection, "select slug from tenants order by slug"))
                    .containsExactly("acme", "atlas");
            assertThat(queryStrings(connection,
                    "select concat(slug, ':', type) from tenants order by slug"))
                    .containsExactly("acme:INDIVIDUAL", "atlas:ORGANIZATION");

            // Force checks the tenant discriminator is gone entirely: the column no longer exists.
            assertThat(columnExists(connection, "users", "tenant_id")).isFalse();
            assertThat(queryStrings(connection,
                    "select concat(email, ':', platform_role, ':', must_change_password) "
                            + "from users where email = 'admin@platform.local'"))
                    .containsExactly("admin@platform.local:SUPER_ADMIN:0");

            // Admins and instructors keep their role through a membership; students do not.
            assertThat(queryLong(connection, "select count(*) from memberships")).isEqualTo(4);
            assertThat(queryStrings(connection,
                    "select concat(u.email, ':', m.role) from memberships m "
                            + "join users u on u.id = m.user_id order by u.email"))
                    .containsExactly(
                            "admin@acme.local:TENANT_ADMIN",
                            "admin@atlas.local:TENANT_ADMIN",
                            "alice+acme@example.com:INSTRUCTOR",
                            "teacher@atlas.local:INSTRUCTOR");
            assertThat(queryLong(connection,
                    "select count(*) from memberships m join users u on u.id = m.user_id "
                            + "where u.email in ('student@atlas.local', 'alice@example.com')"))
                    .isZero();

            // Duplicate emails: the lowest user id keeps the address, the later one is renamed and
            // the rename is written to the append-only audit trail.
            assertThat(queryStrings(connection,
                    "select email from users where email like 'alice%' order by id"))
                    .containsExactly("alice@example.com", "alice+acme@example.com");
            assertThat(queryStrings(connection,
                    "select concat(action, ':', target_id, ':', reason) from admin_audit_log "
                            + "where action = 'MIGRATION_EMAIL_RENAMED'"))
                    .singleElement()
                    .asString()
                    .contains("MIGRATION_EMAIL_RENAMED:", "alice+acme@example.com");

            // A learner refresh session no longer needs a tenant.
            assertThat(columnIsNullable(connection, "refresh_tokens", "tenant_id")).isTrue();
            assertThat(queryLong(connection, "select count(*) from refresh_tokens")).isEqualTo(1);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into refresh_tokens "
                        + "(tenant_id, user_id, token_hash, expires_at, created_at) values "
                        + "(null, 4, '" + "b".repeat(64) + "', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
            }
            assertThat(queryLong(connection,
                    "select count(*) from refresh_tokens where tenant_id is null")).isEqualTo(1);

            assertThat(tableExists(connection, "profiles")).isTrue();
            assertThat(tableExists(connection, "images")).isTrue();
            assertThat(tableExists(connection, "outbox_events")).isTrue();
            assertThat(columnExists(connection, "tenants", "logo_url")).isFalse();
            assertThat(columnExists(connection, "tenants", "logo_image_id")).isTrue();
        }
    }

    @Test
    void keepsAdminAuditLogAppendOnlyAfterMigration() throws Exception {
        resetDatabase();
        migrateTo(MigrationVersion.fromVersion("1"));
        seedV1();
        migrateTo(MigrationVersion.fromVersion("2"));

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into admin_audit_log "
                    + "(actor_user_id, actor_email, action, target_type, target_id, tenant_id, "
                    + "reason, created_at) values "
                    + "(null, 'test@example.local', 'TEST', 'TEST', '1', null, null, UTC_TIMESTAMP(6))");

            assertThatThrownBy(() -> statement.executeUpdate(
                    "update admin_audit_log set reason = 'tampered' where action = 'TEST'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "delete from admin_audit_log where action = 'TEST'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
        }
    }

    /**
     * Both tests share the class container, and one of them migrates the database to V2. Cleaning
     * first keeps each test independent without starting a second MySQL instance.
     */
    private static void resetDatabase() {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
    }

    private static void migrateTo(MigrationVersion target) {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    /**
     * Disable TLS, as the other integration tests do. MySQL 8.4 generates a self-signed server
     * certificate at startup, and a container clock that is ahead of the JVM makes that certificate
     * "not yet valid"; the migration test does not need an encrypted local connection.
     */
    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static void seedV1() throws SQLException {
        String hash = "$2a$10$" + "x".repeat(53);
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into tenants "
                    + "(id, slug, name, status, primary_color, logo_url, created_at, updated_at) values "
                    + "('" + PLATFORM_ID + "', 'platform', 'Sanaa Platform', 'ACTIVE', '#165DFF', "
                    + "'https://cdn.example.test/logo.png', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)), "
                    + "('" + ATLAS_ID + "', 'atlas', 'Atlas Academy', 'ACTIVE', '#165DFF', null, "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)), "
                    + "('" + ACME_ID + "', 'acme', 'Studio Acme', 'ACTIVE', '#00B42A', null, "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");

            insertUser(statement, PLATFORM_ID, "admin@platform.local", "Platform Root",
                    "SUPER_ADMIN", "ACTIVE");
            insertUser(statement, ATLAS_ID, "admin@atlas.local", "Atlas Admin",
                    "TENANT_ADMIN", "ACTIVE");
            insertUser(statement, ATLAS_ID, "teacher@atlas.local", "Atlas Teacher",
                    "INSTRUCTOR", "ACTIVE");
            insertUser(statement, ATLAS_ID, "student@atlas.local", "Atlas Student",
                    "STUDENT", "ACTIVE");
            insertUser(statement, ATLAS_ID, "alice@example.com", "Alice At Atlas",
                    "STUDENT", "ACTIVE");
            insertUser(statement, ACME_ID, "admin@acme.local", "Acme Admin",
                    "TENANT_ADMIN", "ACTIVE");
            insertUser(statement, ACME_ID, "alice@example.com", "Alice At Acme",
                    "INSTRUCTOR", "DISABLED");

            // A V1 refresh session for the Atlas admin. Its id is the lower id of the duplicate pair,
            // so the assertion on the rename can rely on insertion order.
            statement.executeUpdate("insert into refresh_tokens "
                    + "(tenant_id, user_id, token_hash, expires_at, created_at) values "
                    + "('" + ATLAS_ID + "', 2, '" + "a".repeat(64) + "', "
                    + "UTC_TIMESTAMP(6) + interval 7 day, UTC_TIMESTAMP(6))");
        }
    }

    private static void insertUser(Statement statement, String tenantId, String email,
                                   String fullName, String role, String status) throws SQLException {
        statement.executeUpdate("insert into users "
                + "(tenant_id, email, password_hash, full_name, role, status, created_at, updated_at) "
                + "values ('" + tenantId + "', '" + email + "', '$2a$10$" + "x".repeat(53) + "', '"
                + fullName + "', '" + role + "', '" + status + "', UTC_TIMESTAMP(6), "
                + "UTC_TIMESTAMP(6))");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), MYSQL.getUsername(),
                MYSQL.getPassword());
    }

    private static List<String> queryStrings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        return countInformationSchema(connection,
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = '" + table + "'") == 1;
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        return countInformationSchema(connection,
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() and table_name = '" + table
                        + "' and column_name = '" + column + "'") == 1;
    }

    private static boolean columnIsNullable(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select is_nullable from information_schema.columns "
                             + "where table_schema = database() and table_name = '" + table
                             + "' and column_name = '" + column + "'")) {
            resultSet.next();
            return "YES".equals(resultSet.getString(1));
        }
    }

    private static long countInformationSchema(Connection connection, String sql)
            throws SQLException {
        return queryLong(connection, sql);
    }
}
