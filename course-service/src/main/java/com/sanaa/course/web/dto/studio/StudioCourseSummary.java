package com.sanaa.course.web.dto.studio;

import java.math.BigDecimal;
import java.time.Instant;

public record StudioCourseSummary(
        long id,
        String title,
        String summary,
        String status,
        String level,
        String language,
        BigDecimal price,
        BigDecimal effectivePrice,
        String currency,
        BigDecimal discountPrice,
        Instant discountEndsAt,
        Long categoryId,
        String categoryName,
        BigDecimal ratingAvg,
        int ratingCount,
        long enrollmentCount,
        long lessonCount,
        long version,
        Instant updatedAt) {
}
