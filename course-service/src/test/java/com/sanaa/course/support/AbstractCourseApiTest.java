package com.sanaa.course.support;

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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sanaa.common.security.Role;
import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Category;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseLevel;
import com.sanaa.course.domain.CourseStatus;
import com.sanaa.course.domain.Enrollment;
import com.sanaa.course.domain.EnrollmentSource;
import com.sanaa.course.domain.EnrollmentStatus;
import com.sanaa.course.domain.InstructorSnapshot;
import com.sanaa.course.domain.Lesson;
import com.sanaa.course.domain.Module;
import com.sanaa.course.domain.PublisherSnapshot;
import com.sanaa.course.domain.Review;
import com.sanaa.course.repository.CategoryRepository;
import com.sanaa.course.repository.CourseRepository;
import com.sanaa.course.repository.CourseTagRepository;
import com.sanaa.course.repository.EnrollmentRepository;
import com.sanaa.course.repository.InstructorSnapshotRepository;
import com.sanaa.course.repository.LessonRepository;
import com.sanaa.course.repository.ModuleRepository;
import com.sanaa.course.repository.PublisherSnapshotRepository;
import com.sanaa.course.repository.ReviewRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared HTTP, JWT and fixture helpers for the course-service end-to-end tests.
 *
 * <p>Each concrete test class owns its MySQL container (a static {@code @Container} field). A
 * container declared here would be shared by every subclass and stopped after the first class,
 * while the Spring context cache would still point at its old port; keeping one container per class
 * is slower but deterministic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractCourseApiTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final HttpClient HTTP = HttpClient.newHttpClient();

    protected static final String ISSUER = "sanaa-auth";
    protected static final String AUDIENCE = "sanaa-api";
    protected static final String KEY_ID = "test-key";
    protected static final BigDecimal DEFAULT_PRICE = new BigDecimal("299.00");

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @LocalServerPort
    protected int port;

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected ModuleRepository moduleRepository;

    @Autowired
    protected LessonRepository lessonRepository;

    @Autowired
    protected EnrollmentRepository enrollmentRepository;

    @Autowired
    protected ReviewRepository reviewRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected CourseTagRepository courseTagRepository;

    @Autowired
    protected PublisherSnapshotRepository publisherSnapshotRepository;

    @Autowired
    protected InstructorSnapshotRepository instructorSnapshotRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ fixtures

    protected static String newTenantId() {
        return UUID.randomUUID().toString();
    }

    protected long categoryId(String slug) {
        return categoryRepository.findAllByOrderByNameFrAsc().stream()
                .filter(category -> category.getSlug().equals(slug))
                .map(Category::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown category " + slug));
    }

    protected Course saveCourse(String tenantId, long instructorId, String title,
                                CourseStatus status) {
        return saveCourse(tenantId, instructorId, title, status, DEFAULT_PRICE);
    }

    protected Course saveCourse(String tenantId, long instructorId, String title,
                                CourseStatus status, BigDecimal price) {
        Course course = new Course(title, "Résumé de " + title, "Description de " + title,
                CourseLevel.BEGINNER, "fr", price, "MAD", instructorId);
        course.updateCatalogue(categoryId("programmation"), title, "Résumé de " + title,
                "Description de " + title, CourseLevel.BEGINNER, "fr",
                List.of("Compétence " + title), List.of("Prérequis " + title), price, "MAD");
        if (status == CourseStatus.PUBLISHED) {
            course.publish();
        } else if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        return TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
    }

    /**
     * A course that the public catalogue can actually see: an ACTIVE publisher snapshot and an
     * instructor snapshot are required by the visibility rule, so they are created explicitly.
     */
    protected Course publishVisibleCourse(String tenantId, long instructorId, String title,
                                          BigDecimal price) {
        savePublisherSnapshot(tenantId, "pub-" + tenantId.substring(0, 8), "Éditeur " +
                tenantId.substring(0, 8), "ORGANIZATION", "ACTIVE");
        saveInstructorSnapshot(instructorId, tenantId, "Formateur " + instructorId);
        return saveCourse(tenantId, instructorId, title, CourseStatus.PUBLISHED, price);
    }

    protected PublisherSnapshot savePublisherSnapshot(String tenantId, String slug, String name,
                                                      String type, String status) {
        return publisherSnapshotRepository.saveAndFlush(new PublisherSnapshot(
                tenantId, slug, name, type, status, Instant.now()));
    }

    protected PublisherSnapshot suspendPublisher(String tenantId) {
        PublisherSnapshot snapshot = publisherSnapshotRepository.findById(tenantId).orElseThrow();
        snapshot.update(snapshot.getSlug(), snapshot.getName(), snapshot.getType(), "SUSPENDED",
                Instant.now().plusSeconds(5));
        return publisherSnapshotRepository.saveAndFlush(snapshot);
    }

    protected InstructorSnapshot saveInstructorSnapshot(long userId, String tenantId, String name) {
        return instructorSnapshotRepository.saveAndFlush(
                new InstructorSnapshot(userId, tenantId, name, Instant.now()));
    }

    protected Module saveModule(String tenantId, long courseId, String title, int position) {
        return TenantContext.callAs(tenantId,
                () -> moduleRepository.saveAndFlush(new Module(courseId, title, position)));
    }

    protected Lesson saveLesson(String tenantId, long courseId, long moduleId, String title,
                                int position, int durationSeconds, boolean freePreview) {
        return TenantContext.callAs(tenantId, () -> lessonRepository.saveAndFlush(
                new Lesson(courseId, moduleId, title, "Description " + title, position,
                        durationSeconds, freePreview)));
    }

    protected Lesson saveReadyLesson(String tenantId, long courseId, long moduleId, String title,
                                     int position) {
        Lesson lesson = saveLesson(tenantId, courseId, moduleId, title, position, 600, true);
        lesson.markMediaReady();
        return TenantContext.callAs(tenantId, () -> lessonRepository.saveAndFlush(lesson));
    }

    protected Enrollment saveEnrollment(String tenantId, long userId, String userName,
                                        String userEmail, long courseId, EnrollmentSource source) {
        return TenantContext.callAs(tenantId, () -> enrollmentRepository.saveAndFlush(
                new Enrollment(userId, userName, userEmail, courseId, null, source,
                        EnrollmentStatus.ACTIVE, Instant.now())));
    }

    protected Review saveReview(String tenantId, long courseId, long userId, String userName,
                                int rating, String comment) {
        return TenantContext.callAs(tenantId, () -> reviewRepository.saveAndFlush(
                new Review(courseId, userId, userName, rating, comment)));
    }

    // ------------------------------------------------------------------ counters

    /**
     * The denormalised counters read straight from the course row, never through the API: the point
     * of these assertions is that the stored value is right, not that a read model agrees with it.
     */
    protected BigDecimal storedRatingAvg(long courseId) {
        return jdbcTemplate.queryForObject("SELECT rating_avg FROM courses WHERE id = ?",
                BigDecimal.class, courseId);
    }

    protected int storedRatingCount(long courseId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT rating_count FROM courses WHERE id = ?", Integer.class, courseId);
        return value == null ? 0 : value;
    }

    protected long storedStudentCount(long courseId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT student_count FROM courses WHERE id = ?", Long.class, courseId);
        return value == null ? 0 : value;
    }

    /** The same definitions as CourseStatisticsService, computed from the source rows. */
    protected BigDecimal recomputedRatingAvg(long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(ROUND(AVG(rating), 2), 0.00) FROM reviews
                WHERE course_id = ? AND status = 'VISIBLE'
                """, BigDecimal.class, courseId);
    }

    protected long recomputedRatingCount(long courseId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reviews WHERE course_id = ? AND status = 'VISIBLE'
                """, Long.class, courseId);
        return value == null ? 0 : value;
    }

    protected long recomputedStudentCount(long courseId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND status = 'ACTIVE'
                """, Long.class, courseId);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------ tokens

    protected String token(String tenantId, long userId, Role role) {
        return signedToken(tenantId, userId, role, "Utilisateur " + userId,
                "user" + userId + "@example.test", "pub-" + userId, "ORGANIZATION", false);
    }

    protected String token(String tenantId, long userId, Role role, String name, String email,
                           String slug, String type) {
        return signedToken(tenantId, userId, role, name, email, slug, type, false);
    }

    protected String learnerToken(long userId) {
        return learnerToken(userId, "Apprenant " + userId, "learner" + userId + "@example.test");
    }

    protected String learnerToken(long userId, String name, String email) {
        return signedToken(null, userId, Role.STUDENT, name, email, null, null, false);
    }

    protected String mustChangeToken(String tenantId, long userId, Role role) {
        return signedToken(tenantId, userId, role, "Utilisateur " + userId,
                "user" + userId + "@example.test", "pub-" + userId, "ORGANIZATION", true);
    }

    private static String signedToken(String tenantId, long userId, Role role, String name,
                                      String email, String slug, String type,
                                      boolean mustChangePassword) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .subject(Long.toString(userId))
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", role.name())
                    .claim("email", email)
                    .claim("name", name)
                    .claim("must_change_password", mustChangePassword);
            if (tenantId != null) {
                claims.claim("tenant_id", tenantId);
            }
            if (slug != null) {
                claims.claim("tenant_slug", slug);
            }
            if (type != null) {
                claims.claim("tenant_type", type);
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

    // ------------------------------------------------------------------ HTTP

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpResult get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected HttpResult post(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected HttpResult postJson(String path, Object body, String token) throws Exception {
        return send(jsonRequest(path, body, token, "POST"));
    }

    protected HttpResult putJson(String path, Object body, String token) throws Exception {
        return send(jsonRequest(path, body, token, "PUT"));
    }

    protected HttpResult delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .DELETE();
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

    private static HttpResult send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    protected static JsonNode json(HttpResult result) {
        return JSON.readTree(result.body());
    }

    protected static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /** HTTP status and raw body of one call. */
    protected record HttpResult(int status, String body) {
    }

    /**
     * The production decoder talks to auth-service. Tests replace it with a generated RSA key and
     * keep the production issuer and audience validators.
     */
    @TestConfiguration
    public static class JwtTestConfiguration {

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
