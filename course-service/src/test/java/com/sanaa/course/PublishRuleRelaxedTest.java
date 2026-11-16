package com.sanaa.course;

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
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relaxed value of {@code sanaa.course.publish.require-ready-video}.
 *
 * <p>WHY a separate context: the flag is constructor-injected, so the only honest way to test both
 * values is to boot the service twice. The local demo uses this value until the video pipeline
 * exists; the strict rule is covered by {@link StudioApiTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sanaa.course.publish.require-ready-video=false")
@Testcontainers
@Import(AbstractCourseApiTest.JwtTestConfiguration.class)
class PublishRuleRelaxedTest extends AbstractCourseApiTest {

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_publish_relaxed_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Test
    void aCourseWithoutVideoCanBePublishedWhenTheFlagIsOff() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Cours sans vidéo");
        body.put("summary", "Un résumé complet.");
        body.put("description", "Une description complète.");
        body.put("categoryId", categoryId("programmation"));
        body.put("level", "BEGINNER");
        body.put("language", "fr");
        body.put("skills", List.of("Écrire du code"));
        body.put("requirements", List.of("Un ordinateur"));
        body.put("price", DEFAULT_PRICE);
        body.put("currency", "MAD");
        body.put("tags", List.of("java"));

        long courseId = json(postJson("/api/studio/courses", body, instructor))
                .get("id").asLong();
        HttpResult published = post("/api/studio/courses/" + courseId + "/publish", instructor);

        assertThat(published.status()).isEqualTo(200);
        assertThat(json(published).get("status").asString()).isEqualTo("PUBLISHED");
    }

    @Test
    void theOtherCatalogueRulesStillApplyWhenTheFlagIsOff() throws Exception {
        String tenantId = newTenantId();
        String instructor = token(tenantId, 100, Role.INSTRUCTOR);
        long courseId = json(postJson("/api/studio/courses", Map.of("title", "Cours vide"),
                instructor)).get("id").asLong();

        HttpResult rejected = post("/api/studio/courses/" + courseId + "/publish", instructor);

        assertThat(rejected.status()).isEqualTo(422);
        JsonNode body = json(rejected);
        assertThat(body.get("detail").asString())
                .contains("category", "summary", "at least one skill")
                .doesNotContain("ready video");
    }
}
