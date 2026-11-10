package com.sanaa.payment.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The subset of {@code GET /api/courses/{id}} the payment needs. Unknown fields are ignored so a
 * change on the course side does not break payment submission.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseSnapshot(
        Long id,
        String title,
        BigDecimal price,
        String currency,
        String status) {
}
