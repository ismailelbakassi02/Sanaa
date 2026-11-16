package com.sanaa.course;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Modules, lessons and ordering inside the studio, including ownership and read-only statuses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class StudioCurriculumApiTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_curriculum_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Test
    void serverAssignsContiguousPositionsForModulesAndLessons() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours structuré");

        List<Integer> modulePositions = new ArrayList<>();
        long firstModule = 0;
        for (int index = 1; index <= 3; index++) {
            HttpResult response = postJson("/api/studio/courses/" + courseId + "/modules",
                    Map.of("title", "Module " + index), instructor);
            assertThat(response.status()).isEqualTo(201);
            modulePositions.add(json(response).get("position").asInt());
            if (index == 1) {
                firstModule = json(response).get("id").asLong();
            }
        }
        assertThat(modulePositions).containsExactly(1, 2, 3);

        List<Integer> lessonPositions = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            HttpResult response = postJson("/api/studio/modules/" + firstModule + "/lessons",
                    lessonBody("Leçon " + index, index == 1), instructor);
            assertThat(response.status()).isEqualTo(201);
            lessonPositions.add(json(response).get("position").asInt());
        }
        assertThat(lessonPositions).containsExactly(1, 2, 3);

        JsonNode detail = json(get("/api/studio/courses/" + courseId, instructor));
        assertThat(detail.get("modules").size()).isEqualTo(3);
        JsonNode lessons = detail.get("modules").get(0).get("lessons");
        assertThat(lessons.size()).isEqualTo(3);
        assertThat(lessons.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(lessons.get(0).get("freePreview").asBoolean()).isTrue();
        assertThat(lessons.get(1).get("freePreview").asBoolean()).isFalse();
        assertThat(detail.get("lessonCount").asLong()).isEqualTo(3);
    }

    @Test
    void reorderModulesAndLessonsWritesTheNewOrder() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours réordonné");

        long first = addModule(courseId, "Module A", instructor);
        long second = addModule(courseId, "Module B", instructor);
        long third = addModule(courseId, "Module C", instructor);

        HttpResult reordered = putJson("/api/studio/courses/" + courseId + "/modules/order",
                Map.of("ids", List.of(third, first, second)), instructor);
        assertThat(reordered.status()).isEqualTo(200);
        assertThat(ids(reordered)).containsExactly(third, first, second);
        assertThat(positions(reordered)).containsExactly(1, 2, 3);

        long lessonA = addLesson(first, "Leçon A", instructor);
        long lessonB = addLesson(first, "Leçon B", instructor);
        HttpResult lessons = putJson("/api/studio/modules/" + first + "/lessons/order",
                Map.of("ids", List.of(lessonB, lessonA)), instructor);
        assertThat(lessons.status()).isEqualTo(200);
        assertThat(ids(lessons)).containsExactly(lessonB, lessonA);
        assertThat(positions(lessons)).containsExactly(1, 2);

        // Persisted, not only echoed: the studio detail reads the new order back.
        JsonNode detail = json(get("/api/studio/courses/" + courseId, instructor));
        assertThat(detail.get("modules").get(0).get("id").asLong()).isEqualTo(third);
        assertThat(detail.get("modules").get(0).get("lessons").size()).isZero();
        assertThat(detail.get("modules").get(1).get("id").asLong()).isEqualTo(first);
        assertThat(detail.get("modules").get(1).get("lessons").get(0).get("id").asLong())
                .isEqualTo(lessonB);
    }

    @Test
    void reorderRejectsAnIncompleteOrDuplicatedOrder() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours ordre invalide");
        long first = addModule(courseId, "Module A", instructor);
        long second = addModule(courseId, "Module B", instructor);

        HttpResult missing = putJson("/api/studio/courses/" + courseId + "/modules/order",
                Map.of("ids", List.of(first)), instructor);
        assertThat(missing.status()).isEqualTo(400);

        HttpResult duplicate = putJson("/api/studio/courses/" + courseId + "/modules/order",
                Map.of("ids", List.of(first, first)), instructor);
        assertThat(duplicate.status()).isEqualTo(400);

        HttpResult foreign = putJson("/api/studio/courses/" + courseId + "/modules/order",
                Map.of("ids", List.of(first, 999_999L)), instructor);
        assertThat(foreign.status()).isEqualTo(400);

        HttpResult empty = putJson("/api/studio/courses/" + courseId + "/modules/order",
                Map.of("ids", List.of()), instructor);
        assertThat(empty.status()).isEqualTo(400);

        // Nothing was applied by the rejected calls.
        JsonNode detail = json(get("/api/studio/courses/" + courseId, instructor));
        assertThat(detail.get("modules").get(0).get("id").asLong()).isEqualTo(first);
        assertThat(detail.get("modules").get(1).get("id").asLong()).isEqualTo(second);
    }

    @Test
    void renameAndDeleteModuleAlsoRemovesItsLessons() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours avec modules");
        long moduleId = addModule(courseId, "Module initial", instructor);
        long lessonId = addLesson(moduleId, "Leçon", instructor);

        HttpResult renamed = putJson("/api/studio/modules/" + moduleId,
                Map.of("title", "Module renommé"), instructor);
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(json(renamed).get("title").asString()).isEqualTo("Module renommé");
        assertThat(json(renamed).get("lessons").size()).isEqualTo(1);

        assertThat(delete("/api/studio/modules/" + moduleId, instructor).status())
                .isEqualTo(204);
        assertThat(json(get("/api/studio/courses/" + courseId, instructor)).get("modules").size())
                .isZero();
        assertThat(lessonExists(tenantId, lessonId)).isFalse();
    }

    @Test
    void lessonCanBeUpdatedAndDeleted() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours leçons");
        long moduleId = addModule(courseId, "Module", instructor);
        long lessonId = addLesson(moduleId, "Leçon initiale", instructor);

        HttpResult updated = putJson("/api/studio/lessons/" + lessonId,
                Map.of("title", "Leçon modifiée", "description", "Nouveau texte",
                        "durationSeconds", 900, "freePreview", false), instructor);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(json(updated).get("title").asString()).isEqualTo("Leçon modifiée");
        assertThat(json(updated).get("durationSeconds").asInt()).isEqualTo(900);
        assertThat(json(updated).get("freePreview").asBoolean()).isFalse();

        assertThat(delete("/api/studio/lessons/" + lessonId, instructor).status())
                .isEqualTo(204);
        assertThat(lessonExists(tenantId, lessonId)).isFalse();
    }

    @Test
    void curriculumWritesAreScopedByTenantAndInstructor() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        String owner = token(tenantA, 100, Role.INSTRUCTOR);
        // Same numeric user id in another tenant proves that tenant isolation, not ownership alone,
        // rejects the write.
        String otherTenant = token(tenantB, 100, Role.INSTRUCTOR);
        String colleague = token(tenantA, 200, Role.INSTRUCTOR);

        long courseId = createCourse(owner, "Cours privé A");
        long moduleId = addModule(courseId, "Module A", owner);
        long lessonId = addLesson(moduleId, "Leçon A", owner);

        for (String token : List.of(otherTenant, colleague)) {
            assertThat(postJson("/api/studio/courses/" + courseId + "/modules",
                    Map.of("title", "Module pirate"), token).status()).isEqualTo(404);
            assertThat(putJson("/api/studio/modules/" + moduleId,
                    Map.of("title", "Module pirate"), token).status()).isEqualTo(404);
            assertThat(postJson("/api/studio/modules/" + moduleId + "/lessons",
                    lessonBody("Leçon pirate", false), token).status()).isEqualTo(404);
            assertThat(putJson("/api/studio/lessons/" + lessonId,
                    lessonBody("Leçon pirate", false), token).status()).isEqualTo(404);
        }

        JsonNode detail = json(get("/api/studio/courses/" + courseId, owner));
        assertThat(detail.get("modules").size()).isEqualTo(1);
        assertThat(detail.get("modules").get(0).get("lessons").size()).isEqualTo(1);
    }

    @Test
    void suspendedCourseRejectsCurriculumWrites() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours suspendu");
        long moduleId = addModule(courseId, "Module", instructor);
        jdbcTemplate.update(
                "UPDATE courses SET status = 'SUSPENDED', suspended_reason = ? WHERE id = ?",
                "Signalement en cours d'examen", courseId);

        HttpResult rejected = postJson("/api/studio/courses/" + courseId + "/modules",
                Map.of("title", "Module interdit"), instructor);
        assertThat(rejected.status()).isEqualTo(409);
        assertThat(json(rejected).get("detail").asString()).contains("read-only");
        assertThat(putJson("/api/studio/modules/" + moduleId,
                Map.of("title", "Renommé"), instructor).status()).isEqualTo(409);
    }

    @Test
    void parallelModuleCreationsNeverProduceAServerError() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours concurrent");

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            HttpRequest request = concurrentRequest(
                    "/api/studio/courses/" + courseId + "/modules",
                    Map.of("title", "Module parallèle " + index), instructor);
            futures.add(HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<Integer> statuses = futures.stream().map(future -> future.join().statusCode())
                .toList();
        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(201, 409));
        assertThat(statuses).contains(201);
    }

    @Test
    void unknownParentsAndLessonsAreNotFound() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = createCourse(instructor, "Cours existence");

        assertThat(postJson("/api/studio/courses/999999/modules",
                Map.of("title", "Fantôme"), instructor).status()).isEqualTo(404);
        assertThat(postJson("/api/studio/modules/999999/lessons",
                lessonBody("Fantôme", false), instructor).status()).isEqualTo(404);
        assertThat(putJson("/api/studio/lessons/999999",
                lessonBody("Fantôme", false), instructor).status()).isEqualTo(404);
        assertThat(delete("/api/studio/modules/999999", instructor).status()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ helpers

    private long createCourse(String token, String title) throws Exception {
        HttpResult response = postJson("/api/studio/courses", Map.of("title", title), token);
        assertThat(response.status()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    private long addModule(long courseId, String title, String token) throws Exception {
        HttpResult response = postJson("/api/studio/courses/" + courseId + "/modules",
                Map.of("title", title), token);
        assertThat(response.status()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    private long addLesson(long moduleId, String title, String token) throws Exception {
        HttpResult response = postJson("/api/studio/modules/" + moduleId + "/lessons",
                lessonBody(title, true), token);
        assertThat(response.status()).isEqualTo(201);
        return json(response).get("id").asLong();
    }

    private static Map<String, Object> lessonBody(String title, boolean freePreview) {
        return Map.of("title", title, "description", "Description de " + title,
                "durationSeconds", 600, "freePreview", freePreview);
    }

    private static List<Long> ids(HttpResult result) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json(result)) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private static List<Integer> positions(HttpResult result) {
        List<Integer> positions = new ArrayList<>();
        for (JsonNode item : json(result)) {
            positions.add(item.get("position").asInt());
        }
        return positions;
    }

    private HttpRequest concurrentRequest(String path, Object body, String token)
            throws Exception {
        return HttpRequest.newBuilder(java.net.URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
    }

    private boolean lessonExists(String tenantId, long lessonId) {
        LessonRepository repository = lessonRepository;
        return TenantContext.callAs(tenantId,
                () -> repository.findById(lessonId).isPresent());
    }
}
