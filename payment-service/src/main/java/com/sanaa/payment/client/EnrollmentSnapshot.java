package com.sanaa.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of {@code GET /api/enrollments/me}. Only the status and the course id matter here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrollmentSnapshot(
        Long id,
        String status,
        CourseReference course) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourseReference(Long id) {
    }
}
