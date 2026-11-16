package com.sanaa.course.web.dto.studio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StudioCourseDetail(
        long id,
        Long instructorId,
        String title,
        String summary,
        String description,
        String status,
        String level,
        String language,
        Long categoryId,
        String categoryName,
        List<String> skills,
        List<String> requirements,
        List<String> tags,
        BigDecimal price,
        BigDecimal effectivePrice,
        BigDecimal discountPrice,
        Instant discountEndsAt,
        String currency,
        BigDecimal ratingAvg,
        int ratingCount,
        long enrollmentCount,
        long lessonCount,
        String suspendedReason,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<StudioModuleResponse> modules) {
}
