package com.sanaa.course.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.sanaa.course.domain.Course;

public record CourseSummary(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String currency,
        String status,
        Long instructorId,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseSummary from(Course course) {
        return new CourseSummary(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCurrency(),
                course.getStatus().name(),
                course.getInstructorId(),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
