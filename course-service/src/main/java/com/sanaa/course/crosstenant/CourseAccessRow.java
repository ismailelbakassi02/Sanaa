package com.sanaa.course.crosstenant;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The trusted server-side lookup of a purchasable course. The tenant id comes from this row and
 * never from the request, which is what makes a cross-tenant write safe.
 */
public record CourseAccessRow(
        long id,
        String tenantId,
        String title,
        BigDecimal price,
        BigDecimal discountPrice,
        Instant discountEndsAt,
        String currency,
        String publisherName,
        String instructorName,
        Long categoryId) {

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
