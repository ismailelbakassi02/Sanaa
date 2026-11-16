package com.sanaa.course.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guard for the single price rule of the marketplace.
 *
 * <p>The same rule is duplicated as a SQL expression in {@code CatalogueReadRepository} because the
 * price filters and sorts run in the database. The boundary is deliberate: the discount is active
 * only while {@code now} is strictly before its end, so the list price comes back at the exact end
 * instant. Both the API and the SQL expression must agree on that instant.
 */
class CoursePriceTest {

    private static final BigDecimal LIST_PRICE = new BigDecimal("299.00");
    private static final BigDecimal DISCOUNT_PRICE = new BigDecimal("199.00");
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void theListPriceAppliesWithoutADiscount() {
        assertThat(course().effectivePrice(NOW)).isEqualByComparingTo(LIST_PRICE);
    }

    @Test
    void anActiveDiscountReplacesTheListPrice() {
        Course course = course();
        course.applyDiscount(DISCOUNT_PRICE, NOW.plusSeconds(3600));

        assertThat(course.effectivePrice(NOW)).isEqualByComparingTo(DISCOUNT_PRICE);
    }

    @Test
    void theDiscountIsGoneAtTheExactEndInstant() {
        Course course = course();
        Instant endsAt = NOW.plusSeconds(3600);
        course.applyDiscount(DISCOUNT_PRICE, endsAt);

        assertThat(course.effectivePrice(endsAt.minusNanos(1)))
                .isEqualByComparingTo(DISCOUNT_PRICE);
        assertThat(course.effectivePrice(endsAt)).isEqualByComparingTo(LIST_PRICE);
    }

    @Test
    void anExpiredDiscountFallsBackToListPrice() {
        Course course = course();
        course.applyDiscount(DISCOUNT_PRICE, NOW.minusSeconds(1));

        assertThat(course.effectivePrice(NOW)).isEqualByComparingTo(LIST_PRICE);
    }

    @Test
    void anIncompleteDiscountNeverApplies() {
        Course priceOnly = course();
        priceOnly.applyDiscount(DISCOUNT_PRICE, null);
        assertThat(priceOnly.effectivePrice(NOW)).isEqualByComparingTo(LIST_PRICE);

        Course endOnly = course();
        endOnly.applyDiscount(null, NOW.plusSeconds(3600));
        assertThat(endOnly.effectivePrice(NOW)).isEqualByComparingTo(LIST_PRICE);
    }

    @Test
    void clearingADiscountRestoresTheListPrice() {
        Course course = course();
        course.applyDiscount(DISCOUNT_PRICE, NOW.plusSeconds(3600));

        course.clearDiscount();

        assertThat(course.getDiscountPrice()).isNull();
        assertThat(course.getDiscountEndsAt()).isNull();
        assertThat(course.effectivePrice(NOW)).isEqualByComparingTo(LIST_PRICE);
    }

    @Test
    void aFreeCourseStaysFreeWithOrWithoutADiscount() {
        Course course = new Course("Cours gratuit", "Résumé", "Description", CourseLevel.BEGINNER,
                "fr", BigDecimal.ZERO, "MAD", 100L);
        course.applyDiscount(BigDecimal.ZERO, NOW.plusSeconds(3600));

        assertThat(course.effectivePrice(NOW)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aNewCourseIsADraftWithAnEmptyCatalogue() {
        Course course = course();

        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.getSkills()).isEmpty();
        assertThat(course.getRequirements()).isEmpty();
        assertThat(course.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(course.getRatingCount()).isZero();
        assertThat(course.getVersion()).isZero();
    }

    private static Course course() {
        return new Course("Cours", "Résumé", "Description", CourseLevel.BEGINNER, "fr",
                LIST_PRICE, "MAD", 100L);
    }
}
