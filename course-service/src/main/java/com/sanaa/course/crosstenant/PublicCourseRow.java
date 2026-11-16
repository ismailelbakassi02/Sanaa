package com.sanaa.course.crosstenant;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One public course row, read with native SQL across tenants.
 *
 * <p>WHY native SQL: the public catalogue must join the publisher and instructor snapshots of
 * every publisher in one query, which {@code @TenantId} would filter down to one publisher. This
 * package is the only place outside outbox and processed-events inserts where native SQL is
 * allowed; an ArchUnit test fails the build otherwise.
 */
public record PublicCourseRow(
        long id,
        String title,
        String summary,
        String description,
        String level,
        String language,
        BigDecimal price,
        BigDecimal discountPrice,
        Instant discountEndsAt,
        String currency,
        BigDecimal ratingAvg,
        int ratingCount,
        long studentCount,
        long viewCount,
        long enrollmentsLast30Days,
        long instructorId,
        String instructorName,
        String publisherName,
        String publisherSlug,
        String publisherType,
        String categorySlug,
        String categoryName,
        String skillsJson,
        String requirementsJson) {

    /** Same rule as {@code Course.effectivePrice}: the discount ends at the exact instant. */
    public BigDecimal effectivePrice(Instant now) {
        if (discountPrice != null && discountEndsAt != null && now.isBefore(discountEndsAt)) {
            return discountPrice;
        }
        return price;
    }

    public boolean discountActive(Instant now) {
        return discountPrice != null && discountEndsAt != null && now.isBefore(discountEndsAt);
    }
}
