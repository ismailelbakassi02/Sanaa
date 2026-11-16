package com.sanaa.course.web.dto.studio;

import java.time.Instant;

/**
 * A student of one course. {@code email} is null for an INSTRUCTOR: only a TENANT_ADMIN may see
 * learner emails, and the field is omitted from their JSON when null.
 */
public record StudioStudentResponse(
        long enrollmentId,
        long userId,
        String name,
        String email,
        Instant enrolledAt,
        String source,
        String status) {
}
