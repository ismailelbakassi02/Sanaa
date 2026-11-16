package com.sanaa.course.crosstenant;

import java.time.Instant;

public record EnrollmentRow(
        long enrollmentId,
        Instant enrolledAt,
        String source,
        long courseId) {
}
