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
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learner API over HTTP: free enrollment (the stored student counter), the duplicate case, ten
 * concurrent learners, and the per-subject scoping of every learner read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class LearnerApiTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_learner_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Test
    void freeEnrollmentCountsOneStudentAndRepeatsIdempotently() throws Exception {
        String tag = uniqueTag();
        long courseId = visibleFreeCourse(tag, "Cours gratuit " + tag, 2_200);

        HttpResult first = post("/api/courses/" + courseId + "/enroll", learnerToken(4_100));
        assertThat(first.status()).isEqualTo(201);
        assertThat(json(first).get("source").asString()).isEqualTo("FREE");
        assertThat(storedStudentCount(courseId)).isEqualTo(1);
        assertThat(recomputedStudentCount(courseId)).isEqualTo(1);

        // The endpoint is idempotent: a second call returns the same access, not a second student.
        HttpResult second = post("/api/courses/" + courseId + "/enroll", learnerToken(4_100));
        assertThat(second.status()).isEqualTo(201);
        assertThat(json(second).get("enrollmentId").asLong())
                .isEqualTo(json(first).get("enrollmentId").asLong());
        assertThat(storedStudentCount(courseId)).isEqualTo(1);
        assertThat(recomputedStudentCount(courseId)).isEqualTo(1);

        JsonNode detail = json(get("/api/public/courses/" + courseId, null));
        assertThat(detail.get("studentCount").asLong()).isEqualTo(1);
    }

    @Test
    void tenParallelFreeEnrollmentsKeepTheStoredCountExact() throws Exception {
        String tag = uniqueTag();
        long courseId = visibleFreeCourse(tag, "Cours très demandé " + tag, 2_210);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<HttpResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                long userId = 4_200 + index;
                tasks.add(() -> post("/api/courses/" + courseId + "/enroll",
                        learnerToken(userId)));
            }
            for (Future<HttpResult> future : pool.invokeAll(tasks)) {
                assertThat(future.get().status()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(recomputedStudentCount(courseId)).isEqualTo(10);
        assertThat(storedStudentCount(courseId)).isEqualTo(10);
        JsonNode detail = json(get("/api/public/courses/" + courseId, null));
        assertThat(detail.get("studentCount").asLong()).isEqualTo(10);
    }

    @Test
    void learnerOnlySeesOwnEnrollmentsAndHomeCourses() throws Exception {
        String tag = uniqueTag();
        long courseA = visibleFreeCourse(tag, "Cours de A " + tag, 2_220);
        long courseB = visibleFreeCourse(tag, "Cours de B " + tag, 2_221);

        assertThat(post("/api/courses/" + courseA + "/enroll", learnerToken(4_300)).status())
                .isEqualTo(201);
        assertThat(post("/api/courses/" + courseB + "/enroll", learnerToken(4_301)).status())
                .isEqualTo(201);

        HttpResult mine = get("/api/learner/enrollments", learnerToken(4_300));
        assertThat(mine.status()).isEqualTo(200);
        JsonNode enrollments = json(mine);
        assertThat(enrollments.size()).isEqualTo(1);
        assertThat(enrollments.get(0).get("course").get("id").asLong()).isEqualTo(courseA);
        assertThat(mine.body()).contains("Cours de A " + tag).doesNotContain("Cours de B " + tag);

        JsonNode home = json(get("/api/learner/home", learnerToken(4_300)));
        assertThat(home.get("myCourses").size()).isEqualTo(1);
        assertThat(home.get("myCourses").get(0).get("course").get("id").asLong())
                .isEqualTo(courseA);

        // The other learner sees only their own course, so the filter is not inverted either.
        JsonNode other = json(get("/api/learner/enrollments", learnerToken(4_301)));
        assertThat(other.size()).isEqualTo(1);
        assertThat(other.get(0).get("course").get("id").asLong()).isEqualTo(courseB);
    }

    // ------------------------------------------------------------------ my-review

    @Test
    void myReviewReturnsOnlyTheCallersOwnReview() throws Exception {
        String tag = uniqueTag();
        long courseId = visibleFreeCourse(tag, "Cours avec avis " + tag, 4_400);
        saveEnrollment(newTenantIdOf(courseId), 4_401, "Apprenant 4401", "a4401@example.test",
                courseId, EnrollmentSource.FREE);
        saveEnrollment(newTenantIdOf(courseId), 4_402, "Apprenant 4402", "a4402@example.test",
                courseId, EnrollmentSource.FREE);

        assertThat(postJson("/api/courses/" + courseId + "/reviews",
                Map.of("rating", 5, "comment", "Excellent cours"),
                learnerToken(4_401)).status()).isEqualTo(201);
        assertThat(postJson("/api/courses/" + courseId + "/reviews",
                Map.of("rating", 3, "comment", "Correct"),
                learnerToken(4_402)).status()).isEqualTo(201);
        // A reply written by the publisher must reach its author with both fields.
        jdbcTemplate.update("""
                UPDATE reviews SET publisher_reply = 'Merci pour ce retour !',
                    replied_at = CURRENT_TIMESTAMP(6)
                WHERE course_id = ? AND user_id = ?
                """, courseId, 4_401);

        HttpResult mine = get("/api/learner/courses/" + courseId + "/my-review",
                learnerToken(4_401));
        assertThat(mine.status()).isEqualTo(200);
        JsonNode review = json(mine);
        assertThat(review.get("courseId").asLong()).isEqualTo(courseId);
        assertThat(review.get("rating").asInt()).isEqualTo(5);
        assertThat(review.get("comment").asString()).isEqualTo("Excellent cours");
        assertThat(review.get("status").asString()).isEqualTo("VISIBLE");
        assertThat(review.get("publisherReply").asString()).isEqualTo("Merci pour ce retour !");
        assertThat(review.get("repliedAt").asString()).isNotBlank();
        assertThat(review.get("createdAt").asString()).isNotBlank();
        assertThat(review.get("updatedAt").asString()).isNotBlank();

        JsonNode other = json(get("/api/learner/courses/" + courseId + "/my-review",
                learnerToken(4_402)));
        assertThat(other.get("rating").asInt()).isEqualTo(3);
        assertThat(other.get("comment").asString()).isEqualTo("Correct");

        // A learner with no review of a course that does have reviews gets a 404, not a row.
        HttpResult none = get("/api/learner/courses/" + courseId + "/my-review",
                learnerToken(4_403));
        assertThat(none.status()).isEqualTo(404);
    }

    @Test
    void myReviewRequiresAToken() throws Exception {
        long courseId = visibleFreeCourse(uniqueTag(), "Cours anonyme", 4_410);
        assertThat(get("/api/learner/courses/" + courseId + "/my-review", null).status())
                .isEqualTo(401);
    }

    @Test
    void hiddenReviewIsStillReturnedToItsAuthor() throws Exception {
        String tag = uniqueTag();
        long courseId = visibleFreeCourse(tag, "Cours modéré " + tag, 4_420);
        String tenantId = newTenantIdOf(courseId);
        saveEnrollment(tenantId, 4_421, "Apprenant 4421", "a4421@example.test", courseId,
                EnrollmentSource.FREE);
        long reviewId = saveReview(tenantId, courseId, 4_421, "Apprenant 4421", 4,
                "Avis masqué").getId();
        jdbcTemplate.update("UPDATE reviews SET status = 'HIDDEN' WHERE id = ?", reviewId);

        HttpResult mine = get("/api/learner/courses/" + courseId + "/my-review",
                learnerToken(4_421));
        assertThat(mine.status()).isEqualTo(200);
        assertThat(json(mine).get("status").asString()).isEqualTo("HIDDEN");

        JsonNode publicReviews = json(get("/api/public/courses/" + courseId + "/reviews", null));
        assertThat(publicReviews.get("totalElements").asLong()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    /** The tenant of a course, read from the row: the learner view never passes a tenant id. */
    private String newTenantIdOf(long courseId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM courses WHERE id = ?",
                String.class, courseId);
    }

    private long visibleFreeCourse(String tag, String title, long instructorId) {
        String tenantId = newTenantId();
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
        return saved.getId();
    }

    private static String uniqueTag() {
        return "l" + UUID.randomUUID().toString().substring(0, 8);
    }
}
