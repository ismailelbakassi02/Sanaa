package com.sanaa.course.web.dto.publicapi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full public course page. {@code instructorId} is included because the public instructor page is
 * addressed by user id; it is not personal data.
 */
public record PublicCourseDetail(
        long id,
        String title,
        String summary,
        String description,
        String level,
        String language,
        BigDecimal price,
        BigDecimal effectivePrice,
        String currency,
        boolean discountActive,
        Instant discountEndsAt,
        BigDecimal ratingAvg,
        int ratingCount,
        long studentCount,
        long viewCount,
        long instructorId,
        String instructorName,
        String publisherName,
        String publisherSlug,
        String publisherType,
        String categorySlug,
        String categoryName,
        List<String> skills,
        List<String> requirements,
        List<String> tags,
        List<PublicModuleResponse> modules) {
}
