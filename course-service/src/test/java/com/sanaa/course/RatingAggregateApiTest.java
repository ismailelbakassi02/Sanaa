package com.sanaa.course;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.sanaa.course.domain.CourseTag;
import com.sanaa.course.domain.Review;
import com.sanaa.course.service.CourseStatisticsService;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The course rating aggregate over HTTP: the stored {@code courses.rating_avg / rating_count} columns
 * after every review path, and the reads that expose them.
 *
 * <p>Every assertion checks the columns read directly from the course row, because the previous
 * defect was invisible to the reviews endpoint: it computed its own aggregate while the stored
 * columns silently stayed at zero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class RatingAggregateApiTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_rating_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Autowired
    private CourseStatisticsService courseStatisticsService;

    @Test
    void firstReviewUpdatesTheStoredAggregateAndBothPublicViews() throws Exception {
        String tag = uniqueTag();
        VisibleCourse course = createVisibleFreeCourse(tag, "Cours noté " + tag, 2_100);

        assertThat(post("/api/courses/" + course.id() + "/enroll", learnerToken(3_100)).status())
                .isEqualTo(201);
        HttpResult review = review(course.id(), 3_100, 5, "Très clair, merci.");
        assertThat(review.status()).isEqualTo(201);

        assertThat(storedRatingAvg(course.id())).isEqualByComparingTo("5.00");
        assertThat(storedRatingCount(course.id())).isEqualTo(1);
        assertThat(storedStudentCount(course.id())).isEqualTo(1);

        JsonNode card = card(tag, course.id());
        assertThat(card.get("ratingAvg").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(card.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(card.get("studentCount").asLong()).isEqualTo(1);

        JsonNode detail = json(get("/api/public/courses/" + course.id(), null));
        assertThat(detail.get("ratingAvg").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(detail.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(detail.get("studentCount").asLong()).isEqualTo(1);
    }

    @Test
    void aSecondReviewByAnotherLearnerAveragesBothRatings() throws Exception {
        String tag = uniqueTag();
        VisibleCourse course = createVisibleFreeCourse(tag, "Cours partagé " + tag, 2_110);
        enroll(course.id(), 3_111);
        enroll(course.id(), 3_112);

        assertThat(review(course.id(), 3_111, 5, "Excellent.").status()).isEqualTo(201);
        assertThat(review(course.id(), 3_112, 3, "Correct, sans plus.").status()).isEqualTo(201);

        assertThat(storedRatingAvg(course.id())).isEqualByComparingTo("4.00");
        assertThat(storedRatingCount(course.id())).isEqualTo(2);
        assertThat(storedRatingAvg(course.id())).isEqualByComparingTo(recomputedRatingAvg(course.id()));

        JsonNode detail = json(get("/api/public/courses/" + course.id(), null));
        assertThat(detail.get("ratingAvg").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(detail.get("ratingCount").asInt()).isEqualTo(2);
    }

    @Test
    void editingAReviewRecomputesInsteadOfAddingASecondRow() throws Exception {
        String tag = uniqueTag();
        VisibleCourse course = createVisibleFreeCourse(tag, "Cours édité " + tag, 2_120);
        enroll(course.id(), 3_121);
        enroll(course.id(), 3_122);
        assertThat(review(course.id(), 3_121, 5, "Premier avis.").status()).isEqualTo(201);
        assertThat(review(course.id(), 3_122, 3, "Deuxième avis.").status()).isEqualTo(201);

        HttpResult edited = putJson("/api/courses/" + course.id() + "/reviews",
                Map.of("rating", 1, "comment", "Avis corrigé après relecture."),
                learnerToken(3_122));
        assertThat(edited.status()).isEqualTo(200);

        assertThat(storedRatingAvg(course.id())).isEqualByComparingTo("3.00");
        assertThat(storedRatingCount(course.id())).isEqualTo(2);
        assertThat(recomputedRatingCount(course.id())).isEqualTo(2);
        assertThat(json(edited).get("rating").asInt()).isEqualTo(1);
    }

    @Test
    void tenParallelReviewsEndWithTheCountOfTen() throws Exception {
        String tag = uniqueTag();
        VisibleCourse course = createVisibleFreeCourse(tag, "Cours très suivi " + tag, 2_130);
        for (int index = 0; index < 10; index++) {
            enroll(course.id(), 3_200 + index);
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<HttpResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                long userId = 3_200 + index;
                int rating = 1 + (index % 5);
                tasks.add(() -> review(course.id(), userId, rating, "Avis de l'apprenant " + userId));
            }
            for (Future<HttpResult> future : pool.invokeAll(tasks)) {
                assertThat(future.get().status()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(storedRatingCount(course.id())).isEqualTo(10);
        assertThat(storedRatingCount(course.id())).isEqualTo(recomputedRatingCount(course.id()));
        assertThat(storedRatingAvg(course.id()))
                .isEqualByComparingTo(recomputedRatingAvg(course.id()))
                .isEqualByComparingTo("3.00");
        assertThat(json(get("/api/public/courses/" + course.id(), null)).get("ratingCount").asInt())
                .isEqualTo(10);
    }

    @Test
    void aHiddenReviewIsExcludedAfterARecompute() throws Exception {
        String tag = uniqueTag();
        VisibleCourse course = createVisibleFreeCourse(tag, "Cours modéré " + tag, 2_140);
        Review visible = saveReview(course.tenantId(), course.id(), 3_300, "Yasmine A.", 5,
                "Très pédagogique.");
        Review hidden = saveReview(course.tenantId(), course.id(), 3_301, "Omar B.", 1,
                "Commentaire masqué par la modération.");
        jdbcTemplate.update("UPDATE reviews SET status = 'HIDDEN', hidden_reason = ? WHERE id = ?",
                "Contenu inapproprié", hidden.getId());

        // The admin moderation task of a later ticket hides a review and calls the same shared
        // recount; this is that call, not a second implementation of the rule.
        TenantContext.callAs(course.tenantId(), () -> {
            courseStatisticsService.recompute(course.id());
            return null;
        });

        assertThat(storedRatingCount(course.id())).isEqualTo(1);
        assertThat(storedRatingAvg(course.id())).isEqualByComparingTo("5.00");
        JsonNode detail = json(get("/api/public/courses/" + course.id(), null));
        assertThat(detail.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(detail.get("ratingAvg").decimalValue()).isEqualByComparingTo("5.00");

        JsonNode reviews = json(get("/api/public/courses/" + course.id() + "/reviews", null));
        assertThat(reviews.get("items").size()).isEqualTo(1);
        assertThat(reviews.get("items").get(0).get("id").asLong()).isEqualTo(visible.getId());
        assertThat(reviews.get("ratingAvg").decimalValue()).isEqualByComparingTo("5.00");
    }

    @Test
    void ratingSortPutsTheBestCourseFirst() throws Exception {
        String tag = uniqueTag();
        VisibleCourse best = createVisibleFreeCourse(tag, "Meilleur " + tag, 2_150);
        VisibleCourse middle = createVisibleFreeCourse(tag, "Moyen " + tag, 2_151);
        VisibleCourse low = createVisibleFreeCourse(tag, "Faible " + tag, 2_152);

        rate(best.id(), 3_400, 5);
        rate(middle.id(), 3_401, 4);
        rate(middle.id(), 3_402, 4);
        rate(low.id(), 3_403, 2);

        JsonNode page = page("/api/public/courses?tag=" + tag + "&sort=rating");
        assertThat(ids(page)).containsExactly(best.id(), middle.id(), low.id());
    }

    @Test
    void studioStatsExposeTheStoredAggregate() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long instructorId = 2_160;
        String instructor = token(tenant, instructorId, com.sanaa.common.security.Role.INSTRUCTOR);
        VisibleCourse first = createVisibleFreeCourse(tag, "Cours cinq " + tag, instructorId, tenant);
        VisibleCourse second = createVisibleFreeCourse(tag, "Cours trois " + tag, instructorId, tenant);

        enroll(first.id(), 3_500);
        enroll(second.id(), 3_501);
        enroll(second.id(), 3_502);
        assertThat(review(first.id(), 3_500, 5, "Parfait.").status()).isEqualTo(201);
        assertThat(review(second.id(), 3_501, 3, "Bien.").status()).isEqualTo(201);
        assertThat(review(second.id(), 3_502, 3, "Bien aussi.").status()).isEqualTo(201);

        HttpResult stats = get("/api/studio/stats", instructor);
        assertThat(stats.status()).isEqualTo(200);
        JsonNode body = json(stats);
        assertThat(body.get("totalCourses").asLong()).isEqualTo(2);
        assertThat(body.get("totalEnrollments").asLong()).isEqualTo(3);
        assertThat(body.get("ratingCount").asInt()).isEqualTo(3);
        // (5.00 * 1 + 3.00 * 2) / 3 = 3.67
        assertThat(body.get("ratingAvg").decimalValue()).isEqualByComparingTo("3.67");
        assertThat(storedRatingAvg(first.id())).isEqualByComparingTo("5.00");
        assertThat(storedRatingAvg(second.id())).isEqualByComparingTo("3.00");
    }

    /**
     * The consistency guard: whatever data the other tests left behind, every stored counter must
     * equal a fresh recount from the source rows. A drift introduced by a new write path fails here
     * even when no single scenario asserts that path.
     */
    @Test
    void storedAggregatesMatchAFreshRecomputationForEveryCourse() {
        List<Long> courseIds = jdbcTemplate.queryForList("SELECT id FROM courses", Long.class);
        assertThat(courseIds).isNotEmpty();
        for (Long courseId : courseIds) {
            assertThat(storedRatingAvg(courseId)).as("rating average of course " + courseId)
                    .isEqualByComparingTo(recomputedRatingAvg(courseId));
            assertThat(storedRatingCount(courseId)).as("rating count of course " + courseId)
                    .isEqualTo(recomputedRatingCount(courseId));
            assertThat(storedStudentCount(courseId)).as("student count of course " + courseId)
                    .isEqualTo(recomputedStudentCount(courseId));
        }
    }

    // ------------------------------------------------------------------ helpers

    private HttpResult review(long courseId, long userId, int rating, String comment)
            throws Exception {
        return postJson("/api/courses/" + courseId + "/reviews",
                Map.of("rating", rating, "comment", comment), learnerToken(userId));
    }

    private void enroll(long courseId, long userId) throws Exception {
        HttpResult response = post("/api/courses/" + courseId + "/enroll", learnerToken(userId));
        assertThat(response.status()).as("enrollment of learner %s", userId).isEqualTo(201);
    }

    private void rate(long courseId, long learnerId, int rating) throws Exception {
        enroll(courseId, learnerId);
        assertThat(review(courseId, learnerId, rating, "Avis de l'apprenant " + learnerId).status())
                .isEqualTo(201);
    }

    private VisibleCourse createVisibleFreeCourse(String tag, String title, long instructorId) {
        return createVisibleFreeCourse(tag, title, instructorId, newTenantId());
    }

    private VisibleCourse createVisibleFreeCourse(String tag, String title, long instructorId,
                                                  String tenantId) {
        savePublisherSnapshot(tenantId, "pub-" + tenantId.substring(0, 8),
                "Éditeur " + tenantId.substring(0, 8), "ORGANIZATION", "ACTIVE");
        saveInstructorSnapshot(instructorId, tenantId, "Formateur " + instructorId);
        Course course = new Course(title, "Résumé de " + title, "Description de " + title,
                CourseLevel.BEGINNER, "fr", BigDecimal.ZERO, "MAD", instructorId);
        course.updateCatalogue(categoryId("programmation"), title, "Résumé de " + title,
                "Description de " + title, CourseLevel.BEGINNER, "fr",
                List.of("Compétence " + title), List.of("Prérequis " + title), BigDecimal.ZERO,
                "MAD");
        course.publish();
        Course saved = TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
        TenantContext.runAs(tenantId, () -> courseTagRepository.saveAndFlush(
                new CourseTag(saved.getId(), tag)));
        return new VisibleCourse(tenantId, instructorId, saved.getId());
    }

    private JsonNode card(String tag, long courseId) throws Exception {
        for (JsonNode item : page("/api/public/courses?tag=" + tag)) {
            if (item.get("id").asLong() == courseId) {
                return item;
            }
        }
        throw new AssertionError("Course " + courseId + " is missing from tag " + tag);
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
        return "r" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record VisibleCourse(String tenantId, long instructorId, long id) {
    }
}
