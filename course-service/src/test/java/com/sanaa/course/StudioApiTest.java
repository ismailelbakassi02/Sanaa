package com.sanaa.course;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.domain.Review;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The publisher studio over HTTP: ownership, catalogue fields, versioning, lifecycle, discounts,
 * students, statistics and review replies.
 *
 * <p>Publishing runs with the strict rule ({@code require-ready-video = true}); the relaxed value is
 * covered by {@link PublishRuleRelaxedTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sanaa.course.publish.require-ready-video=true")
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class StudioApiTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_studio_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    // ------------------------------------------------------------------ creation

    @Test
    void createCourseUsesTheJwtSubjectAndStartsAsDraft() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR, "Yasmine El Amrani",
                "yasmine@atlas.test", "atlas", "ORGANIZATION");

        HttpResult created = postJson("/api/studio/courses", courseBody("JavaScript moderne"),
                instructor);

        assertThat(created.status()).isEqualTo(201);
        JsonNode body = json(created);
        assertThat(body.get("status").asString()).isEqualTo("DRAFT");
        assertThat(body.get("instructorId").asLong()).isEqualTo(100);
        assertThat(body.get("currency").asString()).isEqualTo("MAD");
        assertThat(body.get("version").asLong()).isZero();
        assertThat(body.get("categoryName").asString()).isEqualTo("Programmation");
        assertThat(body.get("effectivePrice").decimalValue())
                .isEqualByComparingTo(DEFAULT_PRICE);
        assertThat(body.get("skills").size()).isEqualTo(2);
        assertThat(body.get("requirements").size()).isEqualTo(1);
        // The tag list is lower-cased and de-duplicated server-side.
        assertThat(body.get("tags").size()).isEqualTo(2);
        assertThat(body.get("tags").get(0).asString()).isEqualTo("java");
        assertThat(body.get("modules").size()).isZero();

        // The first course of a studio creates global placeholders so the public page can join.
        assertThat(publisherSnapshotRepository.findById(tenantId)).isPresent();
        assertThat(instructorSnapshotRepository.findById(100L)).isPresent();
    }

    @Test
    void validationRejectsBadCatalogueInput() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);

        HttpResult blankTitle = postJson("/api/studio/courses",
                Map.of("title", " "), instructor);
        assertThat(blankTitle.status()).isEqualTo(400);
        assertThat(json(blankTitle).get("errors").get("title")).isNotNull();

        HttpResult badLanguage = postJson("/api/studio/courses",
                with(courseBody("Cours valide"), "language", "de"), instructor);
        assertThat(badLanguage.status()).isEqualTo(400);
        assertThat(json(badLanguage).get("errors").get("language")).isNotNull();

        HttpResult negativePrice = postJson("/api/studio/courses",
                with(courseBody("Cours valide"), "price", new BigDecimal("-1.00")), instructor);
        assertThat(negativePrice.status()).isEqualTo(400);
        assertThat(json(negativePrice).get("errors").get("price")).isNotNull();

        HttpResult badLevel = postJson("/api/studio/courses",
                with(courseBody("Cours valide"), "level", "EXPERT"), instructor);
        assertThat(badLevel.status()).isEqualTo(400);
        assertThat(json(badLevel).get("detail").asString()).contains("level");

        HttpResult badTag = postJson("/api/studio/courses",
                with(courseBody("Cours valide"), "tags", List.of("tag/invalide")), instructor);
        assertThat(badTag.status()).isEqualTo(400);
        assertThat(json(badTag).get("detail").asString()).contains("Invalid tag");

        List<String> tooManyTags = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            tooManyTags.add("tag" + index);
        }
        HttpResult tagOverflow = postJson("/api/studio/courses",
                with(courseBody("Cours valide"), "tags", tooManyTags), instructor);
        assertThat(tagOverflow.status()).isEqualTo(400);
        assertThat(json(tagOverflow).get("errors").get("tags")).isNotNull();
    }

    // ------------------------------------------------------------------ access

    @Test
    void studentsSuperAdminsAndTemporaryPasswordsCannotUseTheStudio() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours existant", CourseStatus.DRAFT);
        String student = learnerToken(700);
        String superAdmin = token(tenantId, 999, Role.SUPER_ADMIN);
        String temporary = mustChangeToken(tenantId, 100, Role.INSTRUCTOR);

        for (String token : List.of(student, superAdmin)) {
            assertThat(get("/api/studio/courses", token).status()).isEqualTo(403);
            assertThat(postJson("/api/studio/courses", courseBody("Cours interdit"), token)
                    .status()).isEqualTo(403);
            assertThat(get("/api/studio/courses/" + course.getId(), token).status())
                    .isEqualTo(403);
        }

        HttpResult blocked = get("/api/studio/courses", temporary);
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.body()).contains("Password change required.");

        HttpResult anonymous = get("/api/studio/courses", null);
        assertThat(anonymous.status()).isEqualTo(401);
    }

    @Test
    void instructorOnlySeesOwnCoursesWhileTenantAdminSeesTheWholeTenant() throws Exception {
        String tenantId = newTenantId();
        String instructorA = token(tenantId, 100, Role.INSTRUCTOR);
        String instructorB = token(tenantId, 200, Role.INSTRUCTOR);
        String admin = token(tenantId, 900, Role.TENANT_ADMIN);

        long courseA = json(postJson("/api/studio/courses", courseBody("Cours de A"),
                instructorA)).get("id").asLong();
        long courseB = json(postJson("/api/studio/courses", courseBody("Cours de B"),
                instructorB)).get("id").asLong();

        HttpResult listA = get("/api/studio/courses?page=0&size=50", instructorA);
        assertThat(listA.status()).isEqualTo(200);
        assertThat(listA.body()).contains("Cours de A").doesNotContain("Cours de B");

        HttpResult listAdmin = get("/api/studio/courses?page=0&size=50", admin);
        assertThat(listAdmin.body()).contains("Cours de A", "Cours de B");

        assertThat(get("/api/studio/courses/" + courseA, instructorA).status()).isEqualTo(200);
        assertThat(get("/api/studio/courses/" + courseB, instructorA).status()).isEqualTo(404);
        assertThat(putJson("/api/studio/courses/" + courseB,
                updateBody("Cours piraté", 0L), instructorA).status()).isEqualTo(404);
        assertThat(delete("/api/studio/courses/" + courseB, instructorA).status()).isEqualTo(404);

        // Another publisher's administrator sees nothing, even with a valid course id.
        String otherTenantAdmin = token(newTenantId(), 900, Role.TENANT_ADMIN);
        assertThat(json(get("/api/studio/courses?page=0&size=50", otherTenantAdmin))
                .get("items").size()).isZero();
        assertThat(get("/api/studio/courses/" + courseA, otherTenantAdmin).status())
                .isEqualTo(404);
    }

    @Test
    void studioReadsAndWritesAreIsolatedByTenant() throws Exception {
        String tenantA = newTenantId();
        Course courseA = saveCourse(tenantA, 100, "Cours du tenant A", CourseStatus.DRAFT);
        String otherTenantAdmin = token(newTenantId(), 900, Role.TENANT_ADMIN);

        // The @TenantId filter is the only guard against a valid id from another publisher.
        assertThat(json(get("/api/studio/courses?page=0&size=50", otherTenantAdmin))
                .get("items").size()).isZero();
        for (String path : List.of(
                "/api/studio/courses/" + courseA.getId(),
                "/api/studio/courses/" + courseA.getId() + "/students",
                "/api/studio/courses/" + courseA.getId() + "/reviews")) {
            assertThat(get(path, otherTenantAdmin).status()).as(path).isEqualTo(404);
        }
        assertThat(putJson("/api/studio/courses/" + courseA.getId(),
                updateBody("Cours piraté", 0L), otherTenantAdmin).status()).isEqualTo(404);
        assertThat(postJson("/api/studio/courses/" + courseA.getId() + "/modules",
                Map.of("title", "Module interdit"), otherTenantAdmin).status()).isEqualTo(404);
        assertThat(post("/api/studio/courses/" + courseA.getId() + "/publish", otherTenantAdmin)
                .status()).isEqualTo(404);
    }

    @Test
    void tenantAdminCanEditAnyCourseOfTheTenant() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours d'un autre", CourseStatus.DRAFT);
        String admin = token(tenantId, 900, Role.TENANT_ADMIN);

        HttpResult updated = putJson("/api/studio/courses/" + course.getId(),
                updateBody("Cours corrigé par admin", 0L), admin);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(json(updated).get("title").asString())
                .isEqualTo("Cours corrigé par admin");

        HttpResult module = postJson("/api/studio/courses/" + course.getId() + "/modules",
                Map.of("title", "Module admin"), admin);
        assertThat(module.status()).isEqualTo(201);
    }

    // ------------------------------------------------------------------ versioning and lifecycle

    @Test
    void updateRequiresTheVersionTheClientLastRead() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/studio/courses", courseBody("Cours versionné"),
                instructor)).get("id").asLong();

        HttpResult first = putJson("/api/studio/courses/" + courseId,
                updateBody("Cours versionné v2", 0L), instructor);
        assertThat(first.status()).isEqualTo(200);
        assertThat(json(first).get("version").asLong()).isEqualTo(1);

        HttpResult stale = putJson("/api/studio/courses/" + courseId,
                updateBody("Écriture obsolète", 0L), instructor);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(json(stale).get("title").asString()).isEqualTo("Conflict");
    }

    @Test
    void publishRequiresCatalogueFieldsAndAReadyVideo() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);

        long minimal = json(postJson("/api/studio/courses", Map.of("title", "Cours vide"),
                instructor)).get("id").asLong();
        HttpResult missing = post("/api/studio/courses/" + minimal + "/publish", instructor);
        assertThat(missing.status()).isEqualTo(422);
        assertThat(json(missing).get("detail").asString())
                .contains("category", "summary", "at least one skill",
                        "at least one lesson with a ready video");

        long complete = json(postJson("/api/studio/courses", courseBody("Cours complet"),
                instructor)).get("id").asLong();
        long moduleId = json(postJson("/api/studio/courses/" + complete + "/modules",
                Map.of("title", "Module"), instructor)).get("id").asLong();
        HttpResult lesson = postJson("/api/studio/modules/" + moduleId + "/lessons",
                lessonBody("Leçon", 600), instructor);
        assertThat(lesson.status()).isEqualTo(201);

        HttpResult withoutVideo = post("/api/studio/courses/" + complete + "/publish",
                instructor);
        assertThat(withoutVideo.status()).isEqualTo(422);
        assertThat(json(withoutVideo).get("detail").asString())
                .contains("at least one lesson with a ready video");

        Lesson stored = TenantContext.callAs(tenantId,
                () -> lessonRepository.findById(json(lesson).get("id").asLong()).orElseThrow());
        // A READY video is the only media state this service can currently produce by itself.
        stored.markMediaReady();
        TenantContext.callAs(tenantId, () -> lessonRepository.saveAndFlush(stored));

        HttpResult published = post("/api/studio/courses/" + complete + "/publish",
                instructor);
        assertThat(published.status()).isEqualTo(200);
        assertThat(json(published).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(json(published).get("lessonCount").asLong()).isEqualTo(1);
    }

    @Test
    void archivedCourseIsReadOnly() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/studio/courses", courseBody("Cours à archiver"),
                instructor)).get("id").asLong();
        long moduleId = json(postJson("/api/studio/courses/" + courseId + "/modules",
                Map.of("title", "Module"), instructor)).get("id").asLong();
        Lesson lesson = saveReadyLesson(tenantId, courseId, moduleId, "Leçon", 1);

        assertThat(post("/api/studio/courses/" + courseId + "/publish", instructor).status())
                .isEqualTo(200);
        HttpResult archived = post("/api/studio/courses/" + courseId + "/archive", instructor);
        assertThat(archived.status()).isEqualTo(200);
        assertThat(json(archived).get("status").asString()).isEqualTo("ARCHIVED");

        assertThat(putJson("/api/studio/courses/" + courseId,
                updateBody("Édition interdite", 1L), instructor).status()).isEqualTo(409);
        assertThat(postJson("/api/studio/courses/" + courseId + "/modules",
                Map.of("title", "Module interdit"), instructor).status()).isEqualTo(409);
        assertThat(putJson("/api/studio/modules/" + moduleId,
                Map.of("title", "Renommé"), instructor).status()).isEqualTo(409);
        assertThat(putJson("/api/studio/lessons/" + lesson.getId(),
                lessonBody("Modifiée", 600), instructor).status()).isEqualTo(409);
        assertThat(post("/api/studio/courses/" + courseId + "/publish", instructor).status())
                .isEqualTo(409);
        assertThat(delete("/api/studio/courses/" + courseId + "/discount", instructor).status())
                .isEqualTo(409);
    }

    @Test
    void aCourseWithoutEnrollmentsIsDeletedButOneWithStudentsIsNot() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);

        long deletable = json(postJson("/api/studio/courses", courseBody("Cours jetable"),
                instructor)).get("id").asLong();
        assertThat(delete("/api/studio/courses/" + deletable, instructor).status())
                .isEqualTo(204);
        assertThat(get("/api/studio/courses/" + deletable, instructor).status()).isEqualTo(404);

        long sold = json(postJson("/api/studio/courses", courseBody("Cours vendu"),
                instructor)).get("id").asLong();
        saveEnrollment(tenantId, 700, "Apprenant 700", "learner700@example.test", sold,
                EnrollmentSource.PAYMENT);

        HttpResult refused = delete("/api/studio/courses/" + sold, instructor);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(json(refused).get("detail").asString()).contains("Archive it");
    }

    @Test
    void discountMustBeBelowTheListPriceAndCanBeCleared() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/studio/courses", courseBody("Cours en promotion"),
                instructor)).get("id").asLong();

        Map<String, Object> discount = Map.of(
                "price", new BigDecimal("199.00"),
                "endsAt", Instant.now().plusSeconds(7 * 24 * 3600).toString());
        HttpResult applied = putJson("/api/studio/courses/" + courseId + "/discount", discount,
                instructor);
        assertThat(applied.status()).isEqualTo(200);
        assertThat(json(applied).get("discountPrice").decimalValue())
                .isEqualByComparingTo("199.00");
        assertThat(json(applied).get("effectivePrice").decimalValue())
                .isEqualByComparingTo("199.00");

        HttpResult tooHigh = putJson("/api/studio/courses/" + courseId + "/discount",
                Map.of("price", new BigDecimal("399.00"),
                        "endsAt", Instant.now().plusSeconds(3600).toString()), instructor);
        assertThat(tooHigh.status()).isEqualTo(400);
        assertThat(json(tooHigh).get("detail").asString()).contains("below the list price");

        HttpResult past = putJson("/api/studio/courses/" + courseId + "/discount",
                Map.of("price", new BigDecimal("99.00"),
                        "endsAt", Instant.now().minusSeconds(60).toString()), instructor);
        assertThat(past.status()).isEqualTo(400);
        assertThat(json(past).get("errors").get("endsAt")).isNotNull();

        HttpResult cleared = delete("/api/studio/courses/" + courseId + "/discount", instructor);
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(json(cleared).get("effectivePrice").decimalValue())
                .isEqualByComparingTo(DEFAULT_PRICE);
    }

    // ------------------------------------------------------------------ students, stats, reviews

    @Test
    void studentEmailsAreOnlyVisibleToATenantAdmin() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours suivi", CourseStatus.PUBLISHED);
        saveEnrollment(tenantId, 700, "Amina Zahra", "amina@example.test", course.getId(),
                EnrollmentSource.PAYMENT);
        saveEnrollment(tenantId, 701, "Youssef Nabil", "youssef@example.test", course.getId(),
                EnrollmentSource.FREE);

        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        HttpResult asInstructor = get("/api/studio/courses/" + course.getId() + "/students",
                instructor);
        assertThat(asInstructor.status()).isEqualTo(200);
        JsonNode instructorPage = json(asInstructor);
        assertThat(instructorPage.get("totalElements").asLong()).isEqualTo(2);
        for (JsonNode item : instructorPage.get("items")) {
            assertThat(item.get("email")).isNull();
            assertThat(item.get("name").asString()).isNotBlank();
        }
        assertThat(asInstructor.body()).doesNotContain("amina@example.test",
                "youssef@example.test");

        String admin = token(tenantId, 900, Role.TENANT_ADMIN);
        HttpResult asAdmin = get("/api/studio/courses/" + course.getId() + "/students", admin);
        assertThat(asAdmin.status()).isEqualTo(200);
        assertThat(asAdmin.body()).contains("amina@example.test", "youssef@example.test");
    }

    @Test
    void statisticsAggregateCoursesEnrollmentsAndRatings() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long draft = json(postJson("/api/studio/courses", courseBody("Brouillon"),
                instructor)).get("id").asLong();
        // The studio API always starts a course as DRAFT, so the published fixture is created
        // directly to keep the test about the aggregate, not about the publish rule.
        Course publishedCourse = saveCourse(tenantId, 100, "Publié", CourseStatus.PUBLISHED);
        long published = publishedCourse.getId();

        Course course = TenantContext.callAs(tenantId,
                () -> courseRepository.findById(published).orElseThrow());
        // The stored columns are set directly here: this test is the weighted average of the studio
        // statistics, while the write path that fills the columns is covered by
        // RatingAggregateApiTest.
        course.updateRating(new BigDecimal("4.50"), 1);
        TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
        saveEnrollment(tenantId, 700, "Amina Zahra", "amina@example.test", published,
                EnrollmentSource.PAYMENT);

        HttpResult stats = get("/api/studio/stats", instructor);
        assertThat(stats.status()).isEqualTo(200);
        JsonNode body = json(stats);
        assertThat(body.get("totalCourses").asLong()).isEqualTo(2);
        assertThat(body.get("coursesByStatus").get("DRAFT").asLong()).isEqualTo(1);
        assertThat(body.get("coursesByStatus").get("PUBLISHED").asLong()).isEqualTo(1);
        assertThat(body.get("totalEnrollments").asLong()).isEqualTo(1);
        assertThat(body.get("enrollmentsLast30Days").asLong()).isEqualTo(1);
        assertThat(body.get("ratingAvg").decimalValue()).isEqualByComparingTo("4.50");
        assertThat(body.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(body.has("revenue")).isFalse();
    }

    @Test
    void reviewsAreListedAndCanBeRepliedToOnlyOnce() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours commenté", CourseStatus.PUBLISHED);
        Review review = saveReview(tenantId, course.getId(), 700, "Amina Zahra", 5,
                "Très clair, merci.");
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);

        HttpResult before = get("/api/studio/courses/" + course.getId() + "/reviews",
                instructor);
        assertThat(before.status()).isEqualTo(200);
        assertThat(json(before).get("items").size()).isEqualTo(1);
        assertThat(json(before).get("items").get(0).get("publisherReply")).isNull();

        assertThat(postJson("/api/studio/reviews/" + review.getId() + "/reply",
                Map.of("reply", "Merci pour ce retour."), instructor).status()).isEqualTo(204);

        HttpResult after = get("/api/studio/courses/" + course.getId() + "/reviews",
                instructor);
        assertThat(json(after).get("items").get(0).get("publisherReply").asString())
                .isEqualTo("Merci pour ce retour.");
        assertThat(json(after).get("items").get(0).get("repliedAt")).isNotNull();

        HttpResult second = postJson("/api/studio/reviews/" + review.getId() + "/reply",
                Map.of("reply", "Deuxième réponse"), instructor);
        assertThat(second.status()).isEqualTo(409);
    }

    @Test
    void studioListIsPagedNewestFirstWithASizeCap() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long first = json(postJson("/api/studio/courses", courseBody("Premier"),
                instructor)).get("id").asLong();
        long second = json(postJson("/api/studio/courses", courseBody("Deuxième"),
                instructor)).get("id").asLong();
        long third = json(postJson("/api/studio/courses", courseBody("Troisième"),
                instructor)).get("id").asLong();

        HttpResult page = get("/api/studio/courses?page=0&size=100", instructor);
        assertThat(page.status()).isEqualTo(200);
        JsonNode body = json(page);
        assertThat(body.get("size").asInt()).isEqualTo(50);
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        assertThat(List.of(body.get("items").get(0).get("id").asLong(),
                        body.get("items").get(1).get("id").asLong(),
                        body.get("items").get(2).get("id").asLong()))
                .containsExactly(third, second, first);
    }

    @Test
    void errorsAreProblemDetailWithoutNullFields() throws Exception {
        String tenantId = newTenantId();
        HttpResult response = get("/api/studio/courses/999999",
                token(tenantId, 100, Role.INSTRUCTOR));
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("\"title\":\"Not Found\"");
        assertThat(response.body()).doesNotContain("\"type\":null", "\"properties\":null",
                "\"instance\":null", "stackTrace");
    }

    // ------------------------------------------------------------------ payloads

    private Map<String, Object> courseBody(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("summary", "Résumé de " + title);
        body.put("description", "Description de " + title);
        body.put("categoryId", categoryId("programmation"));
        body.put("level", "BEGINNER");
        body.put("language", "fr");
        body.put("skills", List.of("Java", "Spring"));
        body.put("requirements", List.of("Un ordinateur"));
        body.put("price", DEFAULT_PRICE);
        body.put("currency", "MAD");
        body.put("tags", List.of("Java", "spring", "Java"));
        return body;
    }

    private Map<String, Object> updateBody(String title, long version) {
        Map<String, Object> body = courseBody(title);
        body.put("version", version);
        return body;
    }

    private Map<String, Object> lessonBody(String title, int durationSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Description de " + title);
        body.put("durationSeconds", durationSeconds);
        body.put("freePreview", true);
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }
}
