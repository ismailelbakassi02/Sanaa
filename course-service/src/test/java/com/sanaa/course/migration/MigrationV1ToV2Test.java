package com.sanaa.course.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Proves that V2 transforms a populated V1 database, not only an empty one.
 *
 * <p>Every integration test starts from the final schema, so this is the only guard on the data
 * migration: the backfilled enrollment snapshots, the nullable catalogue columns and the constraints
 * that protect them.
 */
@Testcontainers
class MigrationV1ToV2Test {

    private static final String TENANT_A = "00000000-0000-0000-0000-0000000000a1";
    private static final String TENANT_B = "00000000-0000-0000-0000-0000000000b2";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("course_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void backfillsTheV1RowsIntoCatalogueDefaults() throws Exception {
        migrateV1AndSeed();
        migrateToLatest();

        try (Connection connection = connection()) {
            // The reference data is inserted by the migration itself.
            assertThat(queryLong(connection, "select count(*) from categories")).isEqualTo(12);
            assertThat(queryStrings(connection,
                    "select slug from categories where slug = 'programmation'"))
                    .containsExactly("programmation");

            // An existing course keeps its data and receives the new defaults.
            assertThat(queryStrings(connection,
                    "select concat(title, '|', status, '|', language, '|', price, '|', "
                            + "coalesce(level, 'NULL'), '|', coalesce(summary, 'NULL'), '|', "
                            + "coalesce(skills, 'NULL'), '|', rating_avg, '|', rating_count, '|', "
                            + "student_count) "
                            + "from courses where id = 1"))
                    .containsExactly("Étalonnage et colorimétrie|PUBLISHED|fr|199.00|NULL|NULL|NULL"
                            + "|0.00|0|1");

            // V1 lessons simply have no description.
            assertThat(queryStrings(connection,
                    "select concat(title, '|', coalesce(description, 'NULL'), '|', media_status) "
                            + "from lessons where id = 1"))
                    .containsExactly("Leçon V1|NULL|NONE");

            // V1 enrollments get a readable placeholder name and email, and the PAYMENT source.
            assertThat(queryStrings(connection,
                    "select concat(user_name, '|', user_email, '|', source, '|', status, '|', "
                            + "source_payment_id) from enrollments where id = 1"))
                    .containsExactly("Learner 700|user700@unknown.local|PAYMENT|ACTIVE|42");

            // The new tables exist and the idempotency ledger is untouched.
            for (String table : List.of("course_tags", "publisher_snapshots",
                    "instructor_snapshots", "reviews", "course_views_daily")) {
                assertThat(tableExists(connection, table)).as(table).isTrue();
            }
            assertThat(queryLong(connection, "select count(*) from processed_events")).isEqualTo(1);
        }
    }

    @Test
    void theNewConstraintsRejectInvalidCatalogueData() throws Exception {
        migrateV1AndSeed();
        migrateToLatest();

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            // SUSPENDED is now a legal status; the migration replaced the V1 check.
            statement.executeUpdate("update courses set status = 'SUSPENDED' where id = 1");
            assertThat(queryStrings(connection, "select status from courses where id = 1"))
                    .containsExactly("SUSPENDED");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update courses set status = 'BOGUS' where id = 1"))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate(
                    "update courses set discount_price = price where id = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_courses_discount");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update courses set level = 'EXPERT' where id = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_courses_level");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update courses set language = 'de' where id = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_courses_language");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update courses set student_count = -1 where id = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_courses_student_count");

            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into reviews (tenant_id, course_id, user_id, user_name, rating, status, "
                            + "created_at, updated_at) values ('" + TENANT_A + "', 1, 700, "
                            + "'Apprenant', 6, 'VISIBLE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_reviews_rating");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into reviews (tenant_id, course_id, user_id, user_name, rating, status, "
                            + "created_at, updated_at) values "
                            + "('00000000-0000-0000-0000-000000000000', 1, 700, 'Apprenant', 5, "
                            + "'VISIBLE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_reviews_tenant_not_nil");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into publisher_snapshots (tenant_id, slug, name, type, status, "
                            + "source_occurred_at) values ('" + TENANT_A + "', 'atlas', 'Atlas', "
                            + "'COMPANY', 'ACTIVE', UTC_TIMESTAMP(6))"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_publisher_snapshots_type");
        }
    }

    @Test
    void v3BackfillsTheCountersFromVisibleReviewsAndActiveEnrollments() throws Exception {
        migrateV1AndSeed();
        // Stop at V2: the reviews and enrollments below are the data V3 must count.
        migrateTo(MigrationVersion.fromVersion("2"));

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into reviews "
                    + "(tenant_id, course_id, user_id, user_name, rating, status, created_at, "
                    + "updated_at) values "
                    + "('" + TENANT_A + "', 1, 701, 'Apprenant 701', 5, 'VISIBLE', "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)), "
                    + "('" + TENANT_A + "', 1, 702, 'Apprenant 702', 4, 'VISIBLE', "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)), "
                    + "('" + TENANT_A + "', 1, 703, 'Apprenant 703', 1, 'HIDDEN', "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
            statement.executeUpdate("insert into enrollments "
                    + "(tenant_id, user_id, course_id, status, enrolled_at) values "
                    + "('" + TENANT_A + "', 701, 1, 'ACTIVE', UTC_TIMESTAMP(6)), "
                    + "('" + TENANT_A + "', 702, 1, 'REVOKED', UTC_TIMESTAMP(6))");
        }

        migrateToLatest();

        try (Connection connection = connection()) {
            // Course 1 already has one ACTIVE V1 enrollment (user 700) and gains user 701;
            // user 702 is REVOKED. The hidden review never counts: (5 + 4) / 2 = 4.50.
            assertThat(queryStrings(connection,
                    "select concat(rating_avg, '|', rating_count, '|', student_count) "
                            + "from courses where id = 1"))
                    .containsExactly("4.50|2|2");
            assertThat(queryLong(connection,
                    "select rating_count from courses where id = 2")).isZero();
        }
    }

    @Test
    void theFullTextIndexSearchesWithoutAccentsAndTheTagsAreUniquePerCourse()
            throws Exception {        migrateV1AndSeed();
        migrateToLatest();

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("update courses set title = 'Étalonnage et colorimétrie', "
                    + "summary = 'Apprendre la colorimétrie' where id = 1");

            assertThat(fullTextIndex(connection, "courses", "ft_courses_title_summary"))
                    .isEqualTo("FULLTEXT");
            assertThat(queryLong(connection,
                    "select count(*) from courses where match(title, summary) "
                            + "against('etalonnage' in natural language mode)")).isEqualTo(1);
            assertThat(queryLong(connection,
                    "select count(*) from courses where match(title, summary) "
                            + "against('COLORIMETRIE' in natural language mode)")).isEqualTo(1);
            assertThat(queryLong(connection,
                    "select count(*) from courses where match(title, summary) "
                            + "against('introuvable' in natural language mode)")).isZero();

            statement.executeUpdate("insert into course_tags (tenant_id, course_id, tag) "
                    + "values ('" + TENANT_A + "', 1, 'photo')");
            assertThat(uniqueIndexExists(connection, "course_tags", "uk_course_tags_course_tag"))
                    .isTrue();
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into course_tags (tenant_id, course_id, tag) "
                            + "values ('" + TENANT_A + "', 1, 'photo')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void migrateV1AndSeed() throws SQLException {
        resetDatabase();
        migrateTo(MigrationVersion.fromVersion("1"));
        seedV1();
    }

    private static void migrateToLatest() {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * Both tests share the class container and one of them migrates to the latest version. Cleaning
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

    private static void seedV1() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into courses "
                    + "(tenant_id, title, description, price, currency, status, instructor_id, "
                    + "version, created_at, updated_at) values "
                    + "('" + TENANT_A + "', 'Étalonnage et colorimétrie', "
                    + "'Une description V1', 199.00, 'MAD', 'PUBLISHED', 100, 3, "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)), "
                    + "('" + TENANT_B + "', 'Cours archivé V1', null, 0.00, 'MAD', 'ARCHIVED', "
                    + "200, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");

            statement.executeUpdate("insert into modules "
                    + "(tenant_id, course_id, title, position, created_at, updated_at) values "
                    + "('" + TENANT_A + "', 1, 'Module V1', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");

            statement.executeUpdate("insert into lessons "
                    + "(tenant_id, course_id, module_id, title, position, duration_seconds, "
                    + "media_status, free_preview, created_at, updated_at) values "
                    + "('" + TENANT_A + "', 1, 1, 'Leçon V1', 1, 600, 'NONE', false, "
                    + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");

            statement.executeUpdate("insert into enrollments "
                    + "(tenant_id, user_id, course_id, source_payment_id, status, enrolled_at) "
                    + "values ('" + TENANT_A + "', 700, 1, 42, 'ACTIVE', UTC_TIMESTAMP(6))");

            statement.executeUpdate("insert into processed_events (event_id, processed_at) "
                    + "values ('11111111-1111-1111-1111-111111111111', UTC_TIMESTAMP(6))");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true";
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
        return queryLong(connection, "select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name = '" + table + "'") == 1;
    }

    private static String fullTextIndex(Connection connection, String table, String indexName)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select index_type from information_schema.statistics "
                             + "where table_schema = database() and table_name = '" + table
                             + "' and index_name = '" + indexName + "' limit 1")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static boolean uniqueIndexExists(Connection connection, String table, String indexName)
            throws SQLException {
        return queryLong(connection, "select count(*) from information_schema.statistics "
                + "where table_schema = database() and table_name = '" + table
                + "' and index_name = '" + indexName + "' and non_unique = 0") > 0;
    }
}
