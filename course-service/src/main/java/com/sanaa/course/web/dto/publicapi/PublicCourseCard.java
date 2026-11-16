package com.sanaa.course.web.dto.publicapi;

import java.math.BigDecimal;
import java.util.List;

/**
 * A public course card. It never contains an email, a tenant id, a media path or a payment id:
 * those belong to the private read models (architecture section 5.3).
 */
public record PublicCourseCard(
        long id,
        String title,
        String summary,
        String level,
        String language,
        BigDecimal price,
        BigDecimal effectivePrice,
        String currency,
        boolean discountActive,
        BigDecimal ratingAvg,
        int ratingCount,
        long studentCount,
        String instructorName,
        String publisherName,
        String publisherSlug,
        String publisherType,
        String categorySlug,
        String categoryName,
        List<String> tags) {
}
