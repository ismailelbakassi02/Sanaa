package com.sanaa.course.web.dto.learner;

import java.time.Instant;

import com.sanaa.course.web.dto.publicapi.PublicCourseCard;

/** One of my courses. The course card is the same public card, restricted to my enrollments. */
public record LearnerEnrollmentResponse(
        long enrollmentId,
        Instant enrolledAt,
        String source,
        PublicCourseCard course) {
}
