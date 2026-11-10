package com.sanaa.course.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CourseDetailResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String currency,
        String status,
        Long instructorId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ModuleSummary> modules) {
}
