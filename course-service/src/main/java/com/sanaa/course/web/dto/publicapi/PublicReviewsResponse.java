package com.sanaa.course.web.dto.publicapi;

import java.math.BigDecimal;
import java.util.List;

/** A page of visible reviews plus the rating aggregate they are computed from. */
public record PublicReviewsResponse(
        List<PublicReviewResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal ratingAvg,
        int ratingCount) {
}
