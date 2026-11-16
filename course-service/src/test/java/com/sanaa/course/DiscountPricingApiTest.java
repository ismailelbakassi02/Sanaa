package com.sanaa.course;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.sanaa.common.tenant.TenantContext;
import com.sanaa.course.domain.Course;
import com.sanaa.course.domain.CourseLevel;
import com.sanaa.course.domain.CourseTag;
import com.sanaa.course.support.AbstractCourseApiTest;
import com.sanaa.course.support.TestDatabases;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The effective price seen by the catalogue SQL must equal {@link Course#effectivePrice(Instant)}.
 *
 * <p>WHY a frozen clock: the SQL and the Java rule must take the same instant, and the instant must
 * come from the injectable {@link Clock}, never from {@code NOW()} in the database. The fixed instant
 * is deliberately in the past relative to the test run: a {@code NOW()} in the SQL would expire every
 * discount below and the assertions would fail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import({AbstractCourseApiTest.JwtTestConfiguration.class,
        DiscountPricingApiTest.FixedClockConfiguration.class})
class DiscountPricingApiTest extends AbstractCourseApiTest {

    static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    @Container
    static final MySQLContainer MYSQL = TestDatabases.mysql("course_discount_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabases.register(registry, MYSQL);
    }

    @Test
    void theSqlEffectivePriceAgreesWithTheJavaRuleForEveryDiscountState() throws Exception {
        String tag = uniqueTag();
        String tenant = newTenantId();
        long instructorId = 2_300;
        savePublisherSnapshot(tenant, "pub-" + tenant.substring(0, 8),
                "Éditeur " + tenant.substring(0, 8), "ORGANIZATION", "ACTIVE");
        saveInstructorSnapshot(instructorId, tenant, "Formateur " + instructorId);
        // The four states of a discount, plus a free course for the free filter.
        long noDiscount = visibleCourse(tag, tenant, instructorId, "Sans promotion " + tag,
                "100.00", null, null);
        long beforeEnd = visibleCourse(tag, tenant, instructorId, "Promotion en cours " + tag,
                "200.00", "40.00", NOW.plusSeconds(3_600));
        long exactlyAtEnd = visibleCourse(tag, tenant, instructorId, "Promotion terminée " + tag,
                "300.00", "50.00", NOW);
        long afterEnd = visibleCourse(tag, tenant, instructorId, "Promotion ancienne " + tag,
                "400.00", "60.00", NOW.minusSeconds(3_600));
        long free = visibleCourse(tag, tenant, instructorId, "Cours gratuit " + tag,
                "0.00", null, null);

        Map<Long, BigDecimal> javaPrices = javaEffectivePrices(List.of(noDiscount, beforeEnd,
                exactlyAtEnd, afterEnd, free));

        assertThat(javaPrices.get(noDiscount)).isEqualByComparingTo("100.00");
        assertThat(javaPrices.get(beforeEnd)).isEqualByComparingTo("40.00");
        assertThat(javaPrices.get(exactlyAtEnd)).isEqualByComparingTo("300.00");
        assertThat(javaPrices.get(afterEnd)).isEqualByComparingTo("400.00");
        assertThat(javaPrices.get(free)).isEqualByComparingTo("0.00");

        // The detail endpoint returns the price the SQL computed with the same instant.
        for (Map.Entry<Long, BigDecimal> entry : javaPrices.entrySet()) {
            JsonNode detail = json(get("/api/public/courses/" + entry.getKey(), null));
            assertThat(detail.get("effectivePrice").decimalValue())
                    .as("effective price of course %s", entry.getKey())
                    .isEqualByComparingTo(entry.getValue());
        }
        JsonNode activeDiscount = json(get("/api/public/courses/" + beforeEnd, null));
        assertThat(activeDiscount.get("discountActive").asBoolean()).isTrue();
        JsonNode expiredExactlyNow = json(get("/api/public/courses/" + exactlyAtEnd, null));
        assertThat(expiredExactlyNow.get("discountActive").asBoolean()).isFalse();
        // Jackson omits a null field, so an expired discount has no discountEndsAt at all.
        assertThat((Object) expiredExactlyNow.get("discountEndsAt")).isNull();

        // The free filter uses the same expression: only the truly free course matches.
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&free=true")))
                .containsExactly(free);

        // minPrice / maxPrice: the expected sets are computed in Java from Course.effectivePrice.
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=40&maxPrice=40")))
                .containsExactlyInAnyOrderElementsOf(elementsWithPriceIn(javaPrices, "40", "40"));
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=41&maxPrice=99")))
                .isEmpty();
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=0&maxPrice=150")))
                .containsExactlyInAnyOrderElementsOf(elementsWithPriceIn(javaPrices, "0", "150"));
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=100&maxPrice=300")))
                .containsExactlyInAnyOrderElementsOf(elementsWithPriceIn(javaPrices, "100", "300"));
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&minPrice=301")))
                .containsExactlyInAnyOrderElementsOf(elementsWithPriceIn(javaPrices, "301", "10000"));

        // Sorting agrees with the Java price and with the SQL tie-break (id descending).
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&sort=price_asc")))
                .containsExactlyElementsOf(sortedByPrice(javaPrices, true));
        assertThat(ids(page("/api/public/courses?tag=" + tag + "&sort=price_desc")))
                .containsExactlyElementsOf(sortedByPrice(javaPrices, false));
    }

    // ------------------------------------------------------------------ helpers

    /** Reads the entity through a fresh transaction and applies the Java rule of one instant. */
    private Map<Long, BigDecimal> javaEffectivePrices(List<Long> courseIds) {
        Map<Long, BigDecimal> prices = new LinkedHashMap<>();
        for (Long courseId : courseIds) {
            Course course = TenantContext.callAs(tenantOf(courseId),
                    () -> courseRepository.findById(courseId).orElseThrow());
            prices.put(courseId, course.effectivePrice(NOW));
        }
        return prices;
    }

    private String tenantOf(long courseId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM courses WHERE id = ?",
                String.class, courseId);
    }

    private static List<Long> elementsWithPriceIn(Map<Long, BigDecimal> prices, String min,
                                                  String max) {
        BigDecimal lower = new BigDecimal(min);
        BigDecimal upper = new BigDecimal(max);
        List<Long> matches = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : prices.entrySet()) {
            if (entry.getValue().compareTo(lower) >= 0 && entry.getValue().compareTo(upper) <= 0) {
                matches.add(entry.getKey());
            }
        }
        return matches;
    }

    private static List<Long> sortedByPrice(Map<Long, BigDecimal> prices, boolean ascending) {
        Comparator<Map.Entry<Long, BigDecimal>> byPrice = Map.Entry.comparingByValue();
        if (!ascending) {
            byPrice = byPrice.reversed();
        }
        Comparator<Map.Entry<Long, BigDecimal>> comparator =
                byPrice.thenComparing(Map.Entry::getKey, Comparator.reverseOrder());
        return prices.entrySet().stream().sorted(comparator).map(Map.Entry::getKey).toList();
    }

    private long visibleCourse(String tag, String tenantId, long instructorId, String title,
                               String price, String discountPrice, Instant discountEndsAt) {
        BigDecimal listPrice = new BigDecimal(price);
        Course course = new Course(title, "Résumé de " + title, "Description de " + title,
                CourseLevel.BEGINNER, "fr", listPrice, "MAD", instructorId);
        course.updateCatalogue(categoryId("programmation"), title, "Résumé de " + title,
                "Description de " + title, CourseLevel.BEGINNER, "fr",
                List.of("Compétence " + title), List.of("Prérequis " + title), listPrice, "MAD");
        course.publish();
        Course saved = TenantContext.callAs(tenantId, () -> courseRepository.saveAndFlush(course));
        TenantContext.runAs(tenantId, () -> courseTagRepository.saveAndFlush(
                new CourseTag(saved.getId(), tag)));
        if (discountPrice != null) {
            jdbcTemplate.update(
                    "UPDATE courses SET discount_price = ?, discount_ends_at = ? WHERE id = ?",
                    new BigDecimal(discountPrice), Timestamp.from(discountEndsAt), saved.getId());
        }
        return saved.getId();
    }

    private JsonNode page(String path) throws Exception {
        HttpResult response = get(path, null);
        assertThat(response.status()).as(path).isEqualTo(200);
        return json(response).get("items");
    }

    private static List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }

    private static String uniqueTag() {
        return "d" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Replaces the production UTC clock for this context only. */
    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
