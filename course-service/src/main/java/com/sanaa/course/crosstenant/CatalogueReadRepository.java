package com.sanaa.course.crosstenant;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The public-catalogue and learner read models.
 *
 * <p>Native SQL is intentional here: both models span publishers, which {@code @TenantId} would
 * forbid. Every statement is read-only except {@link #incrementView}, which writes a counter. The
 * public queries filter on {@code c.status = 'PUBLISHED'} and an ACTIVE publisher snapshot, so a
 * suspended publisher or an unpublished course can never appear.
 */
@Repository
public class CatalogueReadRepository {

    private static final RowMapper<PublicCourseRow> COURSE_MAPPER = (rs, row) -> new PublicCourseRow(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("description"),
            rs.getString("level"),
            rs.getString("language"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("discount_price"),
            instant(rs, "discount_ends_at"),
            rs.getString("currency"),
            rs.getBigDecimal("rating_avg"),
            rs.getInt("rating_count"),
            rs.getLong("student_count"),
            rs.getLong("view_count"),
            rs.getLong("enrollments_30d"),
            rs.getLong("instructor_id"),
            rs.getString("instructor_name"),
            rs.getString("publisher_name"),
            rs.getString("publisher_slug"),
            rs.getString("publisher_type"),
            rs.getString("category_slug"),
            rs.getString("category_name"),
            rs.getString("skills"),
            rs.getString("requirements"));

    private static final String COLUMNS = """
            c.id, c.title, c.summary, c.description, c.level, c.language,
            c.price, c.discount_price, c.discount_ends_at, c.currency,
            c.rating_avg, c.rating_count, c.instructor_id, c.skills, c.requirements,
            i.name AS instructor_name, p.name AS publisher_name, p.slug AS publisher_slug,
            p.type AS publisher_type, cat.slug AS category_slug, cat.name_fr AS category_name,
            (SELECT COUNT(*) FROM enrollments e
                WHERE e.course_id = c.id AND e.status = 'ACTIVE') AS student_count,
            (SELECT COALESCE(SUM(v.views), 0) FROM course_views_daily v
                WHERE v.course_id = c.id AND v.day >= :weekStart) AS views_7d,
            (SELECT COALESCE(SUM(v.views), 0) FROM course_views_daily v
                WHERE v.course_id = c.id) AS view_count,
            (SELECT COUNT(*) FROM enrollments e2
                WHERE e2.course_id = c.id AND e2.status = 'ACTIVE'
                  AND e2.enrolled_at >= :monthStart) AS enrollments_30d
            """;

    /** Inner join on an ACTIVE publisher: the public catalogue visibility rule. */
    private static final String PUBLIC_FROM = """
             FROM courses c
             JOIN publisher_snapshots p ON p.tenant_id = c.tenant_id AND p.status = 'ACTIVE'
             LEFT JOIN categories cat ON cat.id = c.category_id
             LEFT JOIN instructor_snapshots i ON i.user_id = c.instructor_id
            """;

    /**
     * Left join on the publisher: a learner keeps a row for an enrollment they already own even if
     * the publisher was suspended afterwards, so the dashboard never loses a purchase.
     */
    private static final String LEARNER_FROM = """
             FROM courses c
             LEFT JOIN publisher_snapshots p ON p.tenant_id = c.tenant_id
             LEFT JOIN categories cat ON cat.id = c.category_id
             LEFT JOIN instructor_snapshots i ON i.user_id = c.instructor_id
            """;

    /** The effective price, exactly as {@link PublicCourseRow#effectivePrice(Instant)} computes it. */
    private static final String EFFECTIVE_PRICE = """
            (CASE WHEN c.discount_price IS NOT NULL AND c.discount_ends_at IS NOT NULL
                       AND c.discount_ends_at > :now
                  THEN c.discount_price ELSE c.price END)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogueReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryCountRow> categories() {
        return jdbc.query("""
                SELECT cat.id, cat.slug, cat.name_fr,
                       (SELECT COUNT(*) FROM courses c
                           JOIN publisher_snapshots p
                             ON p.tenant_id = c.tenant_id AND p.status = 'ACTIVE'
                           WHERE c.category_id = cat.id AND c.status = 'PUBLISHED') AS course_count
                FROM categories cat
                ORDER BY cat.name_fr ASC
                """, new MapSqlParameterSource(), (rs, row) -> new CategoryCountRow(
                rs.getLong("id"), rs.getString("slug"), rs.getString("name_fr"),
                rs.getLong("course_count")));
    }

    public PublicCoursePage search(PublicCourseSearch search, Instant now, Instant monthStart,
                                   Instant weekStart) {
        MapSqlParameterSource params = baseParams(now, monthStart, weekStart)
                .addValue("limit", search.size())
                .addValue("offset", (long) search.page() * search.size());

        StringBuilder where = new StringBuilder(" WHERE c.status = 'PUBLISHED'");
        appendFilters(where, params, search);

        String orderBy = switch (search.sort()) {
            case "newest" -> "c.created_at DESC, c.id DESC";
            case "rating" -> "c.rating_avg DESC, c.rating_count DESC, c.id DESC";
            case "price_asc" -> EFFECTIVE_PRICE + " ASC, c.id DESC";
            case "price_desc" -> EFFECTIVE_PRICE + " DESC, c.id DESC";
            // Trending: enrollments of the last 30 days, then views of the last 7 days. The two
            // windows make a spike in either signal count without letting an old course dominate.
            default -> "enrollments_30d DESC, views_7d DESC, c.rating_avg DESC, c.id DESC";
        };

        List<PublicCourseRow> items = jdbc.query(
                "SELECT " + COLUMNS + PUBLIC_FROM + where + " ORDER BY " + orderBy
                        + " LIMIT :limit OFFSET :offset",
                params, COURSE_MAPPER);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM courses c"
                        + " JOIN publisher_snapshots p"
                        + "   ON p.tenant_id = c.tenant_id AND p.status = 'ACTIVE'"
                        + " LEFT JOIN categories cat ON cat.id = c.category_id"
                        + where,
                params, Long.class);
        return new PublicCoursePage(items, total == null ? 0 : total);
    }

    public List<PublicCourseRow> trending(Instant now, Instant monthStart, Instant weekStart,
                                          int limit) {
        return jdbc.query("SELECT " + COLUMNS + PUBLIC_FROM
                        + " WHERE c.status = 'PUBLISHED'"
                        + " ORDER BY enrollments_30d DESC, views_7d DESC, c.rating_avg DESC, c.id DESC"
                        + " LIMIT :limit",
                baseParams(now, monthStart, weekStart).addValue("limit", limit), COURSE_MAPPER);
    }

    public Optional<PublicCourseRow> findPublishedCourse(long courseId, Instant now,
                                                         Instant monthStart, Instant weekStart) {
        List<PublicCourseRow> rows = jdbc.query("SELECT " + COLUMNS + PUBLIC_FROM
                        + " WHERE c.id = :id AND c.status = 'PUBLISHED'",
                baseParams(now, monthStart, weekStart).addValue("id", courseId), COURSE_MAPPER);
        return rows.stream().findFirst();
    }

    /**
     * The trusted lookup used before a cross-tenant write and by the purchase-info endpoint. It
     * returns the tenant of the course, which the caller must never read from the request.
     */
    public Optional<CourseAccessRow> findPurchasableCourse(long courseId, Instant now) {
        List<CourseAccessRow> rows = jdbc.query("""
                        SELECT c.id, c.tenant_id, c.title, c.price, c.discount_price,
                               c.discount_ends_at, c.currency, c.category_id,
                               p.name AS publisher_name, i.name AS instructor_name
                        FROM courses c
                        JOIN publisher_snapshots p
                          ON p.tenant_id = c.tenant_id AND p.status = 'ACTIVE'
                        LEFT JOIN instructor_snapshots i ON i.user_id = c.instructor_id
                        WHERE c.id = :id AND c.status = 'PUBLISHED'
                        """,
                new MapSqlParameterSource("id", courseId), (rs, row) -> new CourseAccessRow(
                        rs.getLong("id"),
                        rs.getString("tenant_id"),
                        rs.getString("title"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("discount_price"),
                        instant(rs, "discount_ends_at"),
                        rs.getString("currency"),
                        rs.getString("publisher_name"),
                        rs.getString("instructor_name"),
                        nullableLong(rs, "category_id")));
        return rows.stream().findFirst();
    }

    public List<PublicCourseRow> findCoursesByIds(Collection<Long> courseIds, Instant now,
                                                  Instant monthStart, Instant weekStart) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + COLUMNS + LEARNER_FROM + " WHERE c.id IN (:ids)",
                baseParams(now, monthStart, weekStart).addValue("ids", courseIds), COURSE_MAPPER);
    }

    public Map<Long, List<String>> tagsFor(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> tags = new LinkedHashMap<>();
        jdbc.query("SELECT course_id, tag FROM course_tags WHERE course_id IN (:ids) ORDER BY tag ASC",
                new MapSqlParameterSource("ids", courseIds), rs -> {
                    tags.computeIfAbsent(rs.getLong("course_id"), key -> new java.util.ArrayList<>())
                            .add(rs.getString("tag"));
                });
        return tags;
    }

    public List<CurriculumModuleRow> modules(long courseId) {
        return jdbc.query("""
                        SELECT id, title, position FROM modules
                        WHERE course_id = :courseId ORDER BY position ASC
                        """,
                new MapSqlParameterSource("courseId", courseId),
                (rs, row) -> new CurriculumModuleRow(rs.getLong("id"), rs.getString("title"),
                        rs.getInt("position")));
    }

    public List<CurriculumLessonRow> lessons(long courseId) {
        return jdbc.query("""
                        SELECT id, module_id, title, description, position, duration_seconds,
                               free_preview, media_status = 'READY' AS available
                        FROM lessons WHERE course_id = :courseId
                        ORDER BY module_id ASC, position ASC
                        """,
                new MapSqlParameterSource("courseId", courseId),
                (rs, row) -> new CurriculumLessonRow(
                        rs.getLong("id"),
                        rs.getLong("module_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("position"),
                        rs.getInt("duration_seconds"),
                        rs.getBoolean("free_preview"),
                        rs.getBoolean("available")));
    }

    public List<EnrollmentRow> activeEnrollments(long userId) {
        return jdbc.query("""
                        SELECT id, enrolled_at, source, course_id FROM enrollments
                        WHERE user_id = :userId AND status = 'ACTIVE'
                        ORDER BY enrolled_at DESC, id DESC
                        """,
                new MapSqlParameterSource("userId", userId),
                (rs, row) -> new EnrollmentRow(rs.getLong("id"), instant(rs, "enrolled_at"),
                        rs.getString("source"), rs.getLong("course_id")));
    }

    public boolean hasActiveEnrollment(long userId, long courseId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM enrollments
                        WHERE user_id = :userId AND course_id = :courseId AND status = 'ACTIVE'
                        """,
                new MapSqlParameterSource("userId", userId).addValue("courseId", courseId),
                Long.class);
        return count != null && count > 0;
    }

    /** Used after a unique-constraint race on free enrollment to read the winner's row. */
    public Optional<EnrollmentRow> activeEnrollment(long userId, long courseId) {
        List<EnrollmentRow> rows = jdbc.query("""
                        SELECT id, enrolled_at, source, course_id FROM enrollments
                        WHERE user_id = :userId AND course_id = :courseId AND status = 'ACTIVE'
                        """,
                new MapSqlParameterSource("userId", userId).addValue("courseId", courseId),
                (rs, row) -> new EnrollmentRow(rs.getLong("id"), instant(rs, "enrolled_at"),
                        rs.getString("source"), rs.getLong("course_id")));
        return rows.stream().findFirst();
    }

    public List<TeacherRow> teachers(long userId) {
        return jdbc.query("""
                        SELECT DISTINCT c.instructor_id, i.name AS instructor_name,
                               p.name AS publisher_name, p.slug AS publisher_slug
                        FROM enrollments e
                        JOIN courses c ON c.id = e.course_id
                        LEFT JOIN instructor_snapshots i ON i.user_id = c.instructor_id
                        LEFT JOIN publisher_snapshots p ON p.tenant_id = c.tenant_id
                        WHERE e.user_id = :userId AND e.status = 'ACTIVE'
                        ORDER BY instructor_name ASC, c.instructor_id ASC
                        """,
                new MapSqlParameterSource("userId", userId),
                (rs, row) -> new TeacherRow(
                        rs.getLong("instructor_id"),
                        rs.getString("instructor_name"),
                        rs.getString("publisher_name"),
                        rs.getString("publisher_slug")));
    }

    public List<Long> enrolledCategoryIds(long userId) {
        return jdbc.query("""
                        SELECT DISTINCT c.category_id FROM enrollments e
                        JOIN courses c ON c.id = e.course_id
                        WHERE e.user_id = :userId AND e.status = 'ACTIVE'
                          AND c.category_id IS NOT NULL
                        """,
                new MapSqlParameterSource("userId", userId),
                (rs, row) -> rs.getLong("category_id"));
    }

    public void incrementView(long courseId, LocalDate day) {
        jdbc.update("""
                        INSERT INTO course_views_daily (course_id, day, views)
                        VALUES (:courseId, :day, 1)
                        ON DUPLICATE KEY UPDATE views = views + 1
                        """,
                new MapSqlParameterSource("courseId", courseId).addValue("day", day));
    }

    public List<ReviewRow> visibleReviews(long courseId, int limit, int offset) {
        return jdbc.query("""
                        SELECT id, user_name, rating, comment, publisher_reply, replied_at, created_at
                        FROM reviews
                        WHERE course_id = :courseId AND status = 'VISIBLE'
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                new MapSqlParameterSource("courseId", courseId)
                        .addValue("limit", limit).addValue("offset", offset),
                (rs, row) -> new ReviewRow(
                        rs.getLong("id"),
                        rs.getString("user_name"),
                        rs.getInt("rating"),
                        rs.getString("comment"),
                        rs.getString("publisher_reply"),
                        instant(rs, "replied_at"),
                        instant(rs, "created_at")));
    }

    public long countVisibleReviews(long courseId) {
        Long count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM reviews
                        WHERE course_id = :courseId AND status = 'VISIBLE'
                        """,
                new MapSqlParameterSource("courseId", courseId), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * The caller's own review of one course, whatever its moderation status.
     *
     * <p>The learner has no tenant, so this goes through the same cross-tenant read model as the
     * rest of the learner view, and the filter is the JWT subject. No endpoint parameter can select
     * another learner's row.
     */
    public Optional<MyReviewRow> myReview(long userId, long courseId) {
        List<MyReviewRow> rows = jdbc.query("""
                        SELECT id, course_id, rating, comment, status, publisher_reply, replied_at,
                               created_at, updated_at
                        FROM reviews
                        WHERE course_id = :courseId AND user_id = :userId
                        """,
                new MapSqlParameterSource("userId", userId).addValue("courseId", courseId),
                (rs, row) -> new MyReviewRow(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getInt("rating"),
                        rs.getString("comment"),
                        rs.getString("status"),
                        rs.getString("publisher_reply"),
                        instant(rs, "replied_at"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at")));
        return rows.stream().findFirst();
    }

    public RatingAggregate ratingAggregate(long courseId) {
        return jdbc.queryForObject("""
                        SELECT COALESCE(AVG(rating), 0) AS average, COUNT(*) AS review_count
                        FROM reviews
                        WHERE course_id = :courseId AND status = 'VISIBLE'
                        """,
                new MapSqlParameterSource("courseId", courseId),
                (rs, row) -> new RatingAggregate(rs.getBigDecimal("average"),
                        rs.getInt("review_count")));
    }

    private static MapSqlParameterSource baseParams(Instant now, Instant monthStart,
                                                    Instant weekStart) {
        return new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("monthStart", Timestamp.from(monthStart))
                .addValue("weekStart", Timestamp.from(weekStart));
    }

    private static void appendFilters(StringBuilder where, MapSqlParameterSource params,
                                      PublicCourseSearch search) {
        if (search.q() != null) {
            // FULLTEXT on title and summary. The column collation is accent and case insensitive,
            // so "etalonnage" finds "Etalonnage et colorimetrie" (tested).
            where.append(" AND MATCH(c.title, c.summary) AGAINST (:q IN NATURAL LANGUAGE MODE)");
            params.addValue("q", search.q());
        }
        if (search.categorySlug() != null) {
            where.append(" AND cat.slug = :categorySlug");
            params.addValue("categorySlug", search.categorySlug());
        }
        if (search.tag() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM course_tags t"
                    + " WHERE t.course_id = c.id AND t.tag = :tag)");
            params.addValue("tag", search.tag());
        }
        if (search.level() != null) {
            where.append(" AND c.level = :level");
            params.addValue("level", search.level());
        }
        if (search.freeOnly()) {
            where.append(" AND ").append(EFFECTIVE_PRICE).append(" = 0");
        }
        if (search.minPrice() != null) {
            where.append(" AND ").append(EFFECTIVE_PRICE).append(" >= :minPrice");
            params.addValue("minPrice", search.minPrice());
        }
        if (search.maxPrice() != null) {
            where.append(" AND ").append(EFFECTIVE_PRICE).append(" <= :maxPrice");
            params.addValue("maxPrice", search.maxPrice());
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
