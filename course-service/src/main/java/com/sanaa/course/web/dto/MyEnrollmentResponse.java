package com.sanaa.course.web.dto;

import java.time.Instant;

public record MyEnrollmentResponse(
        Long id,
        String status,
        Instant enrolledAt,
        CourseSummary course) {
}
