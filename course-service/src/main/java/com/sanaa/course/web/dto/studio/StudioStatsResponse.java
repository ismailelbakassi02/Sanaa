package com.sanaa.course.web.dto.studio;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Studio counters. Revenue and pending receipts belong to payment-service; this service reports
 * content and learning figures only, so an instructor never receives publisher revenue.
 */
public record StudioStatsResponse(
        long totalCourses,
        Map<String, Long> coursesByStatus,
        long totalEnrollments,
        long enrollmentsLast30Days,
        BigDecimal ratingAvg,
        int ratingCount) {
}
