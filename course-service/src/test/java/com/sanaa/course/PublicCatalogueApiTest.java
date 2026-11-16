package com.sanaa.course;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseLevel;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.CourseTag;
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.domain.PublisherSnapshot;
import com.sanaa.course.domain.Review;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The anonymous catalogue over HTTP: visibility, full-text search, filters, sorting, views,
 * reviews and the absence of private fields.
 *
 * <p>Courses created by other test methods share this class database. Every search assertion is
 * therefore scoped by a tag or a title term unique to the test instead of asserting global totals;
 * the category count is asserted as a delta for the same reason.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class PublicCatalogueApiTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_catalogue_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Test
    void categoriesCountOnlyCoursesOfActivePublishers() throws Exception {
        long before = categoryCourseCount("programmation");
        String tenant = newTenantId();
        createVisibleCourse(tenant, 100, "Cours compté", "programmation", CourseLevel.BEGINNER,
                DEFAULT_PRICE, List.of());
        // A draft and a course whose publisher has no snapshot must not count.
        saveCourse(tenant, 100, "Brouillon non compté", CourseStatus.DRAFT);
        saveCourse(newTenantId(), 200, "Sans instantané", CourseStatus.PUBLISHED);

        HttpResult response = get("/api/public/categories", null);
        assertThat(response.status()).isEqualTo(200);
        assertThat(json(response).size()).isEqualTo(12);
        assertThat(categoryCourseCount("programmation")).isEqualTo(before + 1);
        assertThat(response.body()).contains("\"slug\":\"programmation\"",
                "\"name\":\"Programmation\"");
    }

    @Test
    void fullTextSearchIgnoresAccentsAndCase() throws Exception {
        String tenant = newTenantId();
        long courseId = createVisibleCourse(tenant, 130, "Étalonnage et colorimétrie",
                "photographie", CourseLevel.INTERMEDIATE, DEFAULT_PRICE, List.of());

        for (String query : List.of("etalonnage", "ETALONNAGE", "colorimetrie")) {
            HttpResult response = get("/api/public/courses?q=" + query, null);
            assertThat(response.status()).isEqualTo(200);
            assertThat(ids(json(response).get("items"))).contains(courseId);
        }

        HttpResult miss = get("/api/public/courses?q=zzzzintrouvable", null);
        assertThat(json(miss).get("totalElements").asLong()).isZero();
    }

    @Test
    void filtersCombineCategoryTagLevelAndPrice() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long paid = createVisibleCourse(tenant, 140, "Cours payant " + tag, "programmation",
                CourseLevel.BEGINNER, new BigDecimal("100.00"), List.of(tag, "web"));
        long advanced = createVisibleCourse(newTenantId(), 141, "Cours avancé " + tag,
                "design-graphique", CourseLevel.ADVANCED, BigDecimal.ZERO, List.of(tag));

        assertThat(ids(page("/api/public/courses?tag=" + tag))).containsExactlyInAnyOrder(paid,
                advanced);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&category=design-graphique")))
                .containsExactly(advanced);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&level=ADVANCED")))
                .containsExactly(advanced);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&free=true")))
                .containsExactly(advanced);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=50&maxPrice=200")))
                .containsExactly(paid);

        HttpResult badLevel = get("/api/public/courses?level=EXPERT", null);
        assertThat(badLevel.status()).isEqualTo(400);
        assertThat(json(badLevel).get("detail").asString()).contains("level");

        HttpResult inverted = get("/api/public/courses?minPrice=200&maxPrice=10", null);
        assertThat(inverted.status()).isEqualTo(400);
        assertThat(json(inverted).get("detail").asString()).contains("minPrice");

        HttpResult negative = get("/api/public/courses?minPrice=-1", null);
        assertThat(negative.status()).isEqualTo(400);
    }

    @Test
    void sortsByEffectivePrice() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long expensive = createVisibleCourse(tenant, 150, "Cher " + tag, "programmation",
                CourseLevel.BEGINNER, new BigDecimal("300.00"), List.of(tag));
        long cheap = createVisibleCourse(tenant, 150, "Bon marché " + tag, "programmation",
                CourseLevel.BEGINNER, new BigDecimal("100.00"), List.of(tag));
        long free = createVisibleCourse(tenant, 150, "Gratuit " + tag, "programmation",
                CourseLevel.BEGINNER, BigDecimal.ZERO, List.of(tag));

        assertThat(ids(page("/api/public/courses?tag=" + tag + "&sort=price_asc")))
                .containsExactly(free, cheap, expensive);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&sort=price_desc")))
                .containsExactly(expensive, cheap, free);

        HttpResult unknownSort = get("/api/public/courses?sort=alphabetique", null);
        assertThat(unknownSort.status()).isEqualTo(400);
        assertThat(json(unknownSort).get("detail").asString()).contains("sort");
    }

    @Test
    void detailReturnsTheCurriculumAndCountsOneViewPerCall() throws Exception {
        String tenant = newTenantId();
        long courseId = createVisibleCourse(tenant, 160, "Cours détaillé", "programmation",
                CourseLevel.BEGINNER, DEFAULT_PRICE, List.of("java"));
        var module = saveModule(tenant, courseId, "Module 1", 1);
        saveReadyLesson(tenant, courseId, module.getId(), "Leçon prête", 1);
        saveLesson(tenant, courseId, module.getId(), "Leçon sans vidéo", 2, 300, false);

        HttpResult first = get("/api/public/courses/" + courseId, null);
        assertThat(first.status()).isEqualTo(200);
        JsonNode body = json(first);
        assertThat(body.get("title").asString()).isEqualTo("Cours détaillé");
        assertThat(body.get("instructorName").asString()).isEqualTo("Formateur 160");
        assertThat(body.get("publisherName").asString()).startsWith("Éditeur ");
        assertThat(body.get("publisherType").asString()).isEqualTo("ORGANIZATION");
        assertThat(body.get("categoryName").asString()).isEqualTo("Programmation");
        assertThat(body.get("skills").size()).isEqualTo(1);
        assertThat(body.get("tags").get(0).asString()).isEqualTo("java");
        assertThat(body.get("discountActive").asBoolean()).isFalse();
        assertThat(body.get("modules").size()).isEqualTo(1);
        JsonNode lessons = body.get("modules").get(0).get("lessons");
        assertThat(lessons.size()).isEqualTo(2);
        assertThat(lessons.get(0).get("available").asBoolean()).isTrue();
        assertThat(lessons.get(1).get("available").asBoolean()).isFalse();
        assertThat(first.body()).doesNotContain("tenantId", "userEmail", "mediaPath",
                "mediaStatus", "sourcePath", "storageKey");

        HttpResult second = get("/api/public/courses/" + courseId, null);
        assertThat(json(second).get("viewCount").asLong())
                .isEqualTo(body.get("viewCount").asLong() + 1);
        Long stored = jdbcTemplate.queryForObject(
                "SELECT views FROM course_views_daily WHERE course_id = ? ORDER BY day DESC LIMIT 1",
                Long.class, courseId);
        assertThat(stored).isEqualTo(2);
    }

    @Test
    void draftSuspendedAndUnknownCoursesAreInvisible() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long draft = createCourseWithSnapshot(tenant, 170, "Cours brouillon caché " + tag,
                CourseStatus.DRAFT, List.of(tag));
        long archived = createCourseWithSnapshot(tenant, 173, "Cours archivé caché " + tag,
                CourseStatus.ARCHIVED, List.of(tag));
        long suspendedStatus = createCourseWithSnapshot(tenant, 174,
                "Cours suspendu par la plateforme " + tag, CourseStatus.PUBLISHED, List.of(tag));
        jdbcTemplate.update("UPDATE courses SET status = 'SUSPENDED', suspended_reason = ? "
                + "WHERE id = ?", "Signalement en cours d'examen", suspendedStatus);

        String suspendedTenant = newTenantId();
        long suspendedPublisher = createVisibleCourse(suspendedTenant, 171,
                "Cours d'un éditeur suspendu " + tag, "programmation", CourseLevel.BEGINNER,
                DEFAULT_PRICE, List.of(tag));
        suspendPublisher(suspendedTenant);

        long noSnapshot = saveCourse(newTenantId(), 172, "Sans instantané", CourseStatus.PUBLISHED)
                .getId();

        for (long hidden : List.of(draft, archived, suspendedStatus, suspendedPublisher,
                noSnapshot)) {
            assertThat(get("/api/public/courses/" + hidden, null).status())
                    .as("detail of hidden course %s", hidden).isEqualTo(404);
        }
        assertThat(get("/api/public/courses/999999999", null).status()).isEqualTo(404);
        // Removing the PUBLISHED filter or the ACTIVE-publisher join makes every course above
        // appear in this page.
        assertThat(ids(page("/api/public/courses?tag=" + tag))).isEmpty();
        assertThat(get("/api/public/courses/" + draft + "/reviews", null).status())
                .isEqualTo(404);
    }

    @Test
    void reviewsAreMaskedAndHiddenReviewsAreExcluded() throws Exception {
        String tenant = newTenantId();
        long courseId = createVisibleCourse(tenant, 180, "Cours commenté", "programmation",
                CourseLevel.BEGINNER, DEFAULT_PRICE, List.of());
        Review visible = saveReview(tenant, courseId, 700, "Yasmine El Amrani", 5,
                "Très pédagogique.");
        Review hidden = saveReview(tenant, courseId, 701, "Omar Benjelloun", 1,
                "Commentaire masqué par la modération.");
        jdbcTemplate.update("UPDATE reviews SET status = 'HIDDEN', hidden_reason = ? WHERE id = ?",
                "Contenu inapproprié", hidden.getId());

        HttpResult response = get("/api/public/courses/" + courseId + "/reviews", null);
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("id").asLong()).isEqualTo(visible.getId());
        assertThat(body.get("items").get(0).get("learnerName").asString())
                .isEqualTo("Yasmine A.");
        assertThat(body.get("ratingAvg").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(body.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(response.body()).doesNotContain("Yasmine El Amrani",
                "Omar Benjelloun", "modération");
    }

    @Test
    void expiredDiscountFallsBackToListPriceInBothTheApiAndTheSqlFilters() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long courseId = createVisibleCourse(tenant, 190, "Promotion terminée " + tag,
                "programmation", CourseLevel.BEGINNER, new BigDecimal("200.00"), List.of(tag));
        jdbcTemplate.update(
                "UPDATE courses SET discount_price = ?, discount_ends_at = ? WHERE id = ?",
                new BigDecimal("50.00"),
                Timestamp.from(Instant.now().minusSeconds(3600)), courseId);

        JsonNode detail = json(get("/api/public/courses/" + courseId, null));
        assertThat(detail.get("effectivePrice").decimalValue())
                .isEqualByComparingTo("200.00");
        assertThat(detail.get("discountActive").asBoolean()).isFalse();

        // The SQL expression must agree with Course.effectivePrice: an expired discount is not a
        // 50 MAD course for a price filter.
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&maxPrice=60")))
                .doesNotContain(courseId);
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&maxPrice=250")))
                .contains(courseId);
    }

    @Test
    void trendingPutsTheMostRecentlyEnrolledCourseFirst() throws Exception {
        String tenant = newTenantId();
        long popular = createVisibleCourse(tenant, 200, "Cours populaire", "programmation",
                CourseLevel.BEGINNER, DEFAULT_PRICE, List.of());
        createVisibleCourse(newTenantId(), 201, "Cours discret", "programmation",
                CourseLevel.BEGINNER, DEFAULT_PRICE, List.of());
        for (long userId = 800; userId < 803; userId++) {
            saveEnrollment(tenant, userId, "Apprenant " + userId,
                    "learner" + userId + "@example.test", popular, EnrollmentSource.PAYMENT);
        }

        HttpResult response = get("/api/public/courses/trending?limit=24", null);
        assertThat(response.status()).isEqualTo(200);
        assertThat(json(response).get(0).get("id").asLong()).isEqualTo(popular);
    }

    @Test
    void pageSizeIsCappedAtTwentyFour() throws Exception {
        HttpResult response = get("/api/public/courses?size=100", null);
        assertThat(response.status()).isEqualTo(200);
        assertThat(json(response).get("size").asInt()).isEqualTo(24);
    }

    @Test
    void catalogueEndpointsAreAnonymousAndReturnNoPrivateFields() throws Exception {
        String tenant = newTenantId();
        long courseId = createVisibleCourse(tenant, 210, "Cours public", "programmation",
                CourseLevel.BEGINNER, DEFAULT_PRICE, List.of());

        for (String path : List.of("/api/public/categories", "/api/public/courses",
                "/api/public/courses/trending", "/api/public/courses/" + courseId,
                "/api/public/courses/" + courseId + "/reviews")) {
            HttpResult response = get(path, null);
            assertThat(response.status()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path)
                    .doesNotContain(tenant, "@example.test", "tenant_id");
        }
    }

    // ------------------------------------------------------------------ helpers

    private long createVisibleCourse(String tenantId, long instructorId, String title,
                                     String categorySlug, CourseLevel level, BigDecimal price,
                                     List<String> tags) {
        savePublisherSnapshot(tenantId, "pub-" + tenantId.substring(0, 8),
                "Éditeur " + tenantId.substring(0, 8), "ORGANIZATION", "ACTIVE");
        saveInstructorSnapshot(instructorId, tenantId, "Formateur " + instructorId);
        return createCourseWithSnapshot(tenantId, instructorId, title, CourseStatus.PUBLISHED,
                tags, categorySlug, level, price);
    }

    private long createCourseWithSnapshot(String tenantId, long instructorId, String title,
                                          CourseStatus status, List<String> tags) {
        return createCourseWithSnapshot(tenantId, instructorId, title, status, tags,
                "programmation", CourseLevel.BEGINNER, DEFAULT_PRICE);
    }

    private long createCourseWithSnapshot(String tenantId, long instructorId, String title,
                                          CourseStatus status, List<String> tags,
                                          String categorySlug, CourseLevel level,
                                          BigDecimal price) {
        long categoryId = categoryId(categorySlug);
        Course course = new Course(title, "Résumé de " + title, "Description de " + title, level,
                "fr", price, "MAD", instructorId);
        course.updateCatalogue(categoryId, title, "Résumé de " + title,
                "Description de " + title, level, "fr",
                List.of("Compétence " + title), List.of("Prérequis " + title), price, "MAD");
        if (status == CourseStatus.PUBLISHED) {
            course.publish();
        } else if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        Course saved = TenantContext.callAs(tenantId,
                () -> courseRepository.saveAndFlush(course));
        if (!tags.isEmpty()) {
            TenantContext.runAs(tenantId, () -> tags.forEach(tag ->
                    courseTagRepository.saveAndFlush(new CourseTag(saved.getId(), tag))));
        }
        return saved.getId();
    }

    private long categoryCourseCount(String slug) throws Exception {
        for (JsonNode category : json(get("/api/public/categories", null))) {
            if (slug.equals(category.get("slug").asString())) {
                return category.get("courseCount").asLong();
            }
        }
        throw new AssertionError("Category " + slug + " is missing");
    }

    private JsonNode page(String path) throws Exception {
        HttpResult response = get(path, null);
        assertThat(response.status()).as(path).isEqualTo(200);
        return json(response).get("items");
    }

    private static List<Long> ids(JsonNode items) {
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private static String uniqueTag() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }
}
