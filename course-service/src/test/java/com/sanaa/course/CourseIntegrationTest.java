package com.sanaa.course;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end HTTP tests against a real MySQL instance. Tokens are signed with a generated RSA key
 * and validated by a test {@link JwtDecoder} that keeps the production issuer and audience rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(CourseIntegrationTest.JwtTestConfiguration.class)
class CourseIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final BigDecimal PRICE = new BigDecimal("299.00");
    private static final String ISSUER = "sanaa-auth";
    private static final String AUDIENCE = "sanaa-api";
    private static final String KEY_ID = "test-key";

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("course_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // SSL is disabled only because the container certificate can be ahead of the host clock on
        // Windows, which makes the driver reject the handshake.
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("management.server.port", () -> "0");
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- creation and validation

    @Test
    void createCourseUsesTheJwtSubjectAndStartsAsDraft() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 42, Role.INSTRUCTOR);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Apprendre Java");
        body.put("description", "Un cours complet pour débuter.");
        body.put("price", PRICE);
        body.put("currency", "MAD");

        HttpResult created = postJson("/api/courses", body, token);
        assertThat(created.status()).isEqualTo(201);
        JsonNode json = json(created);
        assertThat(json.get("title").asString()).isEqualTo("Apprendre Java");
        assertThat(json.get("status").asString()).isEqualTo("DRAFT");
        assertThat(json.get("instructorId").asLong()).isEqualTo(42);
        assertThat(json.get("currency").asString()).isEqualTo("MAD");
        assertThat(json.get("version").asLong()).isZero();
        assertThat(json.get("modules").size()).isZero();
    }

    @Test
    void validationRejectsBadCourseInput() throws Exception {
        String tenantId = newTenantId();
        String token = token(tenantId, 42, Role.INSTRUCTOR);

        HttpResult shortTitle = postJson("/api/courses",
                courseBody("ab", PRICE, "MAD"), token);
        assertThat(shortTitle.status()).isEqualTo(400);
        assertThat(json(shortTitle).get("errors").get("title")).isNotNull();

        HttpResult tooPrecise = postJson("/api/courses",
                courseBody("Cours valide", new BigDecimal("1.234"), "MAD"), token);
        assertThat(tooPrecise.status()).isEqualTo(400);
        assertThat(json(tooPrecise).get("errors").get("price")).isNotNull();

        HttpResult wrongCurrency = postJson("/api/courses",
                courseBody("Cours valide", PRICE, "EUR"), token);
        assertThat(wrongCurrency.status()).isEqualTo(400);
        assertThat(json(wrongCurrency).get("errors").get("currency")).isNotNull();
    }

    // ---------------------------------------------------------------- visibility and roles

    @Test
    void studentSeesPublishedCoursesOnlyAndCannotWrite() throws Exception {
        String tenantId = newTenantId();
        Course published = saveCourse(tenantId, 100, "Cours publié", CourseStatus.PUBLISHED);
        Course draft = saveCourse(tenantId, 100, "Cours brouillon", CourseStatus.DRAFT);
        Course archived = saveCourse(tenantId, 100, "Cours archivé", CourseStatus.ARCHIVED);
        String student = token(tenantId, 200, Role.STUDENT);

        HttpResult list = get("/api/courses?page=0&size=20", student);
        assertThat(list.status()).isEqualTo(200);
        JsonNode items = json(list).get("items");
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("id").asLong()).isEqualTo(published.getId());
        assertThat(list.body()).doesNotContain("Cours brouillon", "Cours archivé");

        assertThat(get("/api/courses/" + published.getId(), student).status()).isEqualTo(200);
        assertThat(get("/api/courses/" + draft.getId(), student).status()).isEqualTo(404);
        assertThat(get("/api/courses/" + archived.getId(), student).status()).isEqualTo(404);

        assertThat(postJson("/api/courses", courseBody("Nouveau cours", PRICE, "MAD"), student)
                .status()).isEqualTo(403);
        assertThat(putJson("/api/courses/" + published.getId(),
                updateBody("Cours publié", PRICE, 0), student).status()).isEqualTo(403);
        assertThat(patch("/api/courses/" + published.getId() + "/publish", student).status())
                .isEqualTo(403);
        assertThat(postJson("/api/courses/" + published.getId() + "/modules",
                Map.of("title", "Module interdit"), student).status()).isEqualTo(403);
    }

    @Test
    void instructorCanSeeOthersPublishedCoursesButCannotEditThem() throws Exception {
        String tenantId = newTenantId();
        Course published = saveCourse(tenantId, 100, "Cours de A", CourseStatus.PUBLISHED);
        Course draft = saveCourse(tenantId, 100, "Brouillon de A", CourseStatus.DRAFT);
        String otherInstructor = token(tenantId, 200, Role.INSTRUCTOR);

        assertThat(get("/api/courses/" + published.getId(), otherInstructor).status())
                .isEqualTo(200);
        assertThat(get("/api/courses/" + draft.getId(), otherInstructor).status())
                .isEqualTo(404);

        HttpResult update = putJson("/api/courses/" + published.getId(),
                updateBody("Tentative", PRICE, 0), otherInstructor);
        assertThat(update.status()).isEqualTo(403);
        assertThat(update.body()).doesNotContain("\"properties\":null", "\"type\":null");

        assertThat(postJson("/api/courses/" + published.getId() + "/modules",
                Map.of("title", "Module interdit"), otherInstructor).status()).isEqualTo(403);
        assertThat(putJson("/api/courses/" + draft.getId(),
                updateBody("Tentative", PRICE, 0), otherInstructor).status()).isEqualTo(404);

        HttpResult list = get("/api/courses?page=0&size=20", otherInstructor);
        assertThat(list.body()).contains("Cours de A");
        assertThat(list.body()).doesNotContain("Brouillon de A");
    }

    @Test
    void tenantAdminCanEditAnyCourseOfTheTenant() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours d'un autre", CourseStatus.DRAFT);
        String admin = token(tenantId, 900, Role.TENANT_ADMIN);

        HttpResult updated = putJson("/api/courses/" + course.getId(),
                updateBody("Cours corrigé par admin", PRICE, 0), admin);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(json(updated).get("title").asString()).isEqualTo("Cours corrigé par admin");
        assertThat(json(updated).get("version").asLong()).isEqualTo(1);

        HttpResult module = postJson("/api/courses/" + course.getId() + "/modules",
                Map.of("title", "Module admin"), admin);
        assertThat(module.status()).isEqualTo(201);
        long moduleId = json(module).get("id").asLong();

        HttpResult lesson = postJson("/api/modules/" + moduleId + "/lessons",
                lessonBody("Leçon admin", 300, false), admin);
        assertThat(lesson.status()).isEqualTo(201);

        HttpResult published = patch("/api/courses/" + course.getId() + "/publish", admin);
        assertThat(published.status()).isEqualTo(200);
        assertThat(json(published).get("status").asString()).isEqualTo("PUBLISHED");
    }

    @Test
    void superAdminIsRejectedOnEveryCourseModuleLessonAndEnrollmentEndpoint() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours atlas", CourseStatus.DRAFT);
        Module module = saveModule(tenantId, course.getId(), "Module atlas", 1);
        String superAdmin = token(tenantId, 999, Role.SUPER_ADMIN);

        List<HttpResult> responses = List.of(
                get("/api/courses?page=0&size=20", superAdmin),
                get("/api/courses/" + course.getId(), superAdmin),
                postJson("/api/courses", courseBody("Cours interdit", PRICE, "MAD"), superAdmin),
                putJson("/api/courses/" + course.getId(),
                        updateBody("Cours interdit", PRICE, 0), superAdmin),
                patch("/api/courses/" + course.getId() + "/publish", superAdmin),
                patch("/api/courses/" + course.getId() + "/archive", superAdmin),
                postJson("/api/courses/" + course.getId() + "/modules",
                        Map.of("title", "Module interdit"), superAdmin),
                postJson("/api/modules/" + module.getId() + "/lessons",
                        lessonBody("Leçon interdite", 60, false), superAdmin),
                get("/api/enrollments/me", superAdmin));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.status()).isEqualTo(403);
            assertThat(response.body()).doesNotContain("\"properties\":null", "\"type\":null");
        });
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        assertThat(get("/api/courses", null).status()).isEqualTo(401);
        assertThat(get("/api/courses/1", null).status()).isEqualTo(401);
        assertThat(postJson("/api/courses", courseBody("Cours sans jeton", PRICE, "MAD"), null)
                .status()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- rules

    @Test
    void publishWithoutLessonsReturnsUnprocessableEntity() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/courses",
                courseBody("Cours sans leçon", PRICE, "MAD"), instructor)).get("id").asLong();

        HttpResult response = patch("/api/courses/" + courseId + "/publish", instructor);
        assertThat(response.status()).isEqualTo(422);
        assertThat(json(response).get("detail").asString()).contains("lesson");
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/courses",
                courseBody("Cours versionné", PRICE, "MAD"), instructor)).get("id").asLong();

        HttpResult first = putJson("/api/courses/" + courseId,
                updateBody("Cours versionné v2", PRICE, 0), instructor);
        assertThat(first.status()).isEqualTo(200);
        assertThat(json(first).get("version").asLong()).isEqualTo(1);

        HttpResult stale = putJson("/api/courses/" + courseId,
                updateBody("Écriture obsolète", PRICE, 0), instructor);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(json(stale).get("title").asString()).isEqualTo("Conflict");
    }

    @Test
    void pageSizeIsCappedAtFiftyAndOrderIsNewestFirst() throws Exception {
        String tenantId = newTenantId();
        Course first = saveCourse(tenantId, 100, "Premier", CourseStatus.DRAFT);
        Course second = saveCourse(tenantId, 100, "Deuxième", CourseStatus.DRAFT);
        Course third = saveCourse(tenantId, 100, "Troisième", CourseStatus.DRAFT);
        String admin = token(tenantId, 900, Role.TENANT_ADMIN);

        HttpResult response = get("/api/courses?page=0&size=100", admin);
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("size").asInt()).isEqualTo(50);
        assertThat(body.get("items").size()).isEqualTo(3);
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("items").get(0).get("id").asLong()).isEqualTo(third.getId());
        assertThat(body.get("items").get(1).get("id").asLong()).isEqualTo(second.getId());
        assertThat(body.get("items").get(2).get("id").asLong()).isEqualTo(first.getId());
    }

    @Test
    void serverAssignsModuleAndLessonPositionsAndIgnoresClientValues() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/courses",
                courseBody("Cours structuré", PRICE, "MAD"), instructor)).get("id").asLong();

        List<Integer> modulePositions = new ArrayList<>();
        long firstModuleId = 0;
        for (int index = 1; index <= 3; index++) {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Module " + index);
            body.put("position", 99);
            HttpResult response = postJson("/api/courses/" + courseId + "/modules", body,
                    instructor);
            assertThat(response.status()).isEqualTo(201);
            modulePositions.add(json(response).get("position").asInt());
            if (index == 1) {
                firstModuleId = json(response).get("id").asLong();
            }
        }
        assertThat(modulePositions).containsExactly(1, 2, 3);

        List<Integer> lessonPositions = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            Map<String, Object> body = lessonBody("Leçon " + index, 120 + index, index == 1);
            body.put("position", 99);
            HttpResult response = postJson("/api/modules/" + firstModuleId + "/lessons", body,
                    instructor);
            assertThat(response.status()).isEqualTo(201);
            lessonPositions.add(json(response).get("position").asInt());
        }
        assertThat(lessonPositions).containsExactly(1, 2, 3);

        JsonNode detail = json(get("/api/courses/" + courseId, instructor));
        assertThat(detail.get("modules").size()).isEqualTo(3);
        JsonNode lessons = detail.get("modules").get(0).get("lessons");
        assertThat(lessons.size()).isEqualTo(3);
        assertThat(lessons.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(lessons.get(0).get("freePreview").asBoolean()).isTrue();
        assertThat(lessons.get(1).get("freePreview").asBoolean()).isFalse();
    }

    @Test
    void parallelModuleCreationsNeverProduceAServerError() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/courses",
                courseBody("Cours concurrent", PRICE, "MAD"), instructor)).get("id").asLong();

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            HttpRequest request = jsonRequest("/api/courses/" + courseId + "/modules",
                    Map.of("title", "Module parallèle " + index), instructor, "POST");
            futures.add(HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<Integer> statuses = futures.stream().map(future -> future.join().statusCode()).toList();
        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(201, 409));
        assertThat(statuses).contains(201);
    }

    @Test
    void archivedCourseRejectsEveryEdit() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/courses",
                courseBody("Cours archivé", PRICE, "MAD"), instructor)).get("id").asLong();
        long moduleId = json(postJson("/api/courses/" + courseId + "/modules",
                Map.of("title", "Module"), instructor)).get("id").asLong();
        postJson("/api/modules/" + moduleId + "/lessons",
                lessonBody("Leçon", 60, false), instructor);

        assertThat(patch("/api/courses/" + courseId + "/publish", instructor).status())
                .isEqualTo(200);
        assertThat(patch("/api/courses/" + courseId + "/archive", instructor).status())
                .isEqualTo(200);

        assertThat(putJson("/api/courses/" + courseId,
                updateBody("Édition interdite", PRICE, 1), instructor).status()).isEqualTo(409);
        assertThat(postJson("/api/courses/" + courseId + "/modules",
                Map.of("title", "Module interdit"), instructor).status()).isEqualTo(409);
        assertThat(postJson("/api/modules/" + moduleId + "/lessons",
                lessonBody("Leçon interdite", 60, false), instructor).status()).isEqualTo(409);
        assertThat(patch("/api/courses/" + courseId + "/publish", instructor).status())
                .isEqualTo(409);
    }

    @Test
    void detailReturnsModulesAndLessonSummariesWithoutMediaFields() throws Exception {
        String tenantId = newTenantId();
        Course course = saveCourse(tenantId, 100, "Cours détaillé", CourseStatus.PUBLISHED);
        Module second = saveModule(tenantId, course.getId(), "Module 2", 2);
        Module first = saveModule(tenantId, course.getId(), "Module 1", 1);
        saveLesson(tenantId, course.getId(), first.getId(), "Leçon 1", 1, 90, true);
        saveLesson(tenantId, course.getId(), second.getId(), "Leçon 2", 1, 120, false);

        HttpResult response = get("/api/courses/" + course.getId(),
                token(tenantId, 700, Role.STUDENT));
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("modules").size()).isEqualTo(2);
        assertThat(body.get("modules").get(0).get("id").asLong()).isEqualTo(first.getId());
        assertThat(body.get("modules").get(0).get("position").asInt()).isEqualTo(1);
        JsonNode lesson = body.get("modules").get(0).get("lessons").get(0);
        assertThat(lesson.get("title").asString()).isEqualTo("Leçon 1");
        assertThat(lesson.get("durationSeconds").asInt()).isEqualTo(90);
        assertThat(lesson.get("freePreview").asBoolean()).isTrue();
        assertThat(lesson.get("id")).isNotNull();
        assertThat(response.body()).doesNotContain("mediaStatus", "media_status");
    }

    @Test
    void errorsAreProblemDetailWithoutNullFields() throws Exception {
        String tenantId = newTenantId();
        HttpResult response = get("/api/courses/999999",
                token(tenantId, 700, Role.STUDENT));
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("\"title\":\"Not Found\"");
        assertThat(response.body()).doesNotContain("\"type\":null", "\"properties\":null",
                "\"instance\":null", "stackTrace");
    }

    // ---------------------------------------------------------------- tenant isolation

    @Test
    void tenantAdminCannotReadOrUpdateAnotherTenantsCourse() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        Course courseA = saveCourse(tenantA, 100, "Cours secret A", CourseStatus.DRAFT);
        String adminB = token(tenantB, 900, Role.TENANT_ADMIN);

        assertThat(get("/api/courses/" + courseA.getId(), adminB).status()).isEqualTo(404);
        assertThat(putJson("/api/courses/" + courseA.getId(),
                updateBody("Cours piraté", PRICE, 0), adminB).status()).isEqualTo(404);

        Course unchanged = courseIn(tenantA, courseA.getId());
        assertThat(unchanged.getTitle()).isEqualTo("Cours secret A");
        assertThat(unchanged.getVersion()).isZero();
    }

    @Test
    void listingsNeverContainOtherTenantsRows() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        Course publishedA = saveCourse(tenantA, 100, "Publié A", CourseStatus.PUBLISHED);
        Course draftA = saveCourse(tenantA, 100, "Brouillon A", CourseStatus.DRAFT);
        Course publishedB = saveCourse(tenantB, 200, "Publié B", CourseStatus.PUBLISHED);

        HttpResult adminA = get("/api/courses?page=0&size=50",
                token(tenantA, 900, Role.TENANT_ADMIN));
        assertThat(adminA.status()).isEqualTo(200);
        assertThat(adminA.body()).contains("Publié A", "Brouillon A");
        assertThat(adminA.body()).doesNotContain("Publié B");

        HttpResult adminB = get("/api/courses?page=0&size=50",
                token(tenantB, 900, Role.TENANT_ADMIN));
        assertThat(adminB.body()).contains("Publié B");
        assertThat(adminB.body()).doesNotContain("Publié A", "Brouillon A");

        HttpResult studentA = get("/api/courses?page=0&size=50",
                token(tenantA, 700, Role.STUDENT));
        assertThat(studentA.body()).contains("Publié A");
        assertThat(studentA.body()).doesNotContain("Brouillon A", "Publié B");

        HttpResult instructorA = get("/api/courses?page=0&size=50",
                token(tenantA, 100, Role.INSTRUCTOR));
        assertThat(instructorA.body()).contains("Publié A", "Brouillon A");
        assertThat(instructorA.body()).doesNotContain("Publié B");
    }

    @Test
    void addingModulesOrLessonsToAnotherTenantsCourseReturnsNotFound() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        Course courseA = saveCourse(tenantA, 100, "Cours A", CourseStatus.DRAFT);
        Module moduleA = saveModule(tenantA, courseA.getId(), "Module A", 1);
        String instructorB = token(tenantB, 200, Role.INSTRUCTOR);

        assertThat(postJson("/api/courses/" + courseA.getId() + "/modules",
                Map.of("title", "Module pirate"), instructorB).status()).isEqualTo(404);
        assertThat(postJson("/api/modules/" + moduleA.getId() + "/lessons",
                lessonBody("Leçon pirate", 60, false), instructorB).status()).isEqualTo(404);

        long moduleCount = TenantContext.callAs(tenantA,
                () -> (long) moduleRepository.countByCourseId(courseA.getId()));
        long lessonCount = TenantContext.callAs(tenantA,
                () -> (long) lessonRepository.countByCourseId(courseA.getId()));
        assertThat(moduleCount).isEqualTo(1);
        assertThat(lessonCount).isZero();
    }

    @Test
    void myEnrollmentsNeverShowOtherTenantsRows() throws Exception {
        String tenantA = newTenantId();
        String tenantB = newTenantId();
        Course courseA = saveCourse(tenantA, 100, "Cours Atlas", CourseStatus.PUBLISHED);
        Course courseB = saveCourse(tenantB, 200, "Cours Acme", CourseStatus.PUBLISHED);
        saveEnrollment(tenantA, 700, courseA.getId());
        saveEnrollment(tenantB, 700, courseB.getId());

        HttpResult studentA = get("/api/enrollments/me", token(tenantA, 700, Role.STUDENT));
        assertThat(studentA.status()).isEqualTo(200);
        assertThat(json(studentA).size()).isEqualTo(1);
        assertThat(studentA.body()).contains("Cours Atlas");
        assertThat(studentA.body()).doesNotContain("Cours Acme");

        HttpResult studentB = get("/api/enrollments/me", token(tenantB, 700, Role.STUDENT));
        assertThat(json(studentB).size()).isEqualTo(1);
        assertThat(studentB.body()).contains("Cours Acme");
        assertThat(studentB.body()).doesNotContain("Cours Atlas");
    }

    @Test
    void insertWithoutTenantContextFails() {
        Course course = new Course("Sans tenant", null, PRICE, "MAD", 100L);
        TenantContext.clear();

        assertThatThrownBy(() -> courseRepository.saveAndFlush(course))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownOrMissingTenantIdCannotReadAnything() throws Exception {
        String tenantA = newTenantId();
        Course courseA = saveCourse(tenantA, 100, "Cours A", CourseStatus.PUBLISHED);

        String unknownTenant = token(newTenantId(), 900, Role.TENANT_ADMIN);
        HttpResult unknownList = get("/api/courses?page=0&size=50", unknownTenant);
        assertThat(unknownList.status()).isEqualTo(200);
        assertThat(json(unknownList).get("items").size()).isZero();
        assertThat(get("/api/courses/" + courseA.getId(), unknownTenant).status())
                .isEqualTo(404);

        String missingTenant = token(null, 900, Role.TENANT_ADMIN);
        HttpResult missingList = get("/api/courses?page=0&size=50", missingTenant);
        assertThat(missingList.status()).isEqualTo(200);
        assertThat(json(missingList).get("items").size()).isZero();
        assertThat(get("/api/courses/" + courseA.getId(), missingTenant).status())
                .isEqualTo(404);
    }

    // ---------------------------------------------------------------- metrics

    @Test
    void prometheusIsExposedOnTheManagementPortOnly() throws Exception {
        String tenantId = newTenantId();
        saveCourse(tenantId, 100, "Cours métriques", CourseStatus.PUBLISHED);

        HttpResponse<String> prometheus = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + "/actuator/prometheus"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("application=\"course-service\"");
        assertThat(prometheus.body()).doesNotContain(tenantId);

        HttpResponse<String> mainPort = HTTP.send(HttpRequest.newBuilder(
                URI.create(baseUrl() + "/actuator/prometheus"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(mainPort.statusCode()).isEqualTo(404);
    }

    // ---------------------------------------------------------------- helpers

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String newTenantId() {
        return UUID.randomUUID().toString();
    }

    private Course saveCourse(String tenantId, long instructorId, String title,
                              CourseStatus status) {
        Course course = new Course(title, "Description", PRICE, "MAD", instructorId);
        if (status == CourseStatus.PUBLISHED) {
            course.publish();
        } else if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        return TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
    }

    private Module saveModule(String tenantId, long courseId, String title, int position) {
        return TenantContext.callAs(tenantId, () ->
                moduleRepository.saveAndFlush(new Module(courseId, title, position)));
    }

    private Lesson saveLesson(String tenantId, long courseId, long moduleId, String title,
                              int position, int durationSeconds, boolean freePreview) {
        return TenantContext.callAs(tenantId, () -> lessonRepository.saveAndFlush(
                new Lesson(courseId, moduleId, title, position, durationSeconds, freePreview)));
    }

    private Enrollment saveEnrollment(String tenantId, long userId, long courseId) {
        return TenantContext.callAs(tenantId, () -> enrollmentRepository.saveAndFlush(
                new Enrollment(userId, courseId, null, EnrollmentStatus.ACTIVE, Instant.now())));
    }

    private Course courseIn(String tenantId, long courseId) {
        return TenantContext.callAs(tenantId,
                () -> courseRepository.findById(courseId).orElseThrow());
    }

    private static Map<String, Object> courseBody(String title, BigDecimal price, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Description");
        body.put("price", price);
        body.put("currency", currency);
        return body;
    }

    private static Map<String, Object> updateBody(String title, BigDecimal price, long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Description modifiée");
        body.put("price", price);
        body.put("currency", "MAD");
        body.put("version", version);
        return body;
    }

    private static Map<String, Object> lessonBody(String title, int durationSeconds,
                                                  boolean freePreview) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("durationSeconds", durationSeconds);
        body.put("freePreview", freePreview);
        return body;
    }

    private String token(String tenantId, long userId, Role role) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .subject(Long.toString(userId))
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", role.name())
                    .claim("email", "user" + userId + "@example.test")
                    .claim("name", "Test User");
            if (tenantId != null) {
                claims.claim("tenant_id", tenantId);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResult get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpResult postJson(String path, Object body, String token) throws Exception {
        return send(jsonRequest(path, body, token, "POST"));
    }

    private HttpResult putJson(String path, Object body, String token) throws Exception {
        return send(jsonRequest(path, body, token, "PUT"));
    }

    private HttpResult patch(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .method("PATCH", HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private HttpRequest jsonRequest(String path, Object body, String token, String method)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private static JsonNode json(HttpResult result) {
        return JSON.readTree(result.body());
    }

    private record HttpResult(int status, String body) {
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

    @TestConfiguration
    static class JwtTestConfiguration {

        private static final OAuth2TokenValidator<Jwt> AUDIENCE_VALIDATOR = token ->
                token.getAudience().contains(AUDIENCE)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "The required audience is missing.", null));

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                    (RSAPublicKey) KEY_PAIR.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER), AUDIENCE_VALIDATOR));
            return decoder;
        }
    }
}
