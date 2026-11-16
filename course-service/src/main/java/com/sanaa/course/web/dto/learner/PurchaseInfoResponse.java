package com.sanaa.course.web.dto.learner;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What payment-service needs before creating a payment: the trusted tenant, title, currency and the
 * effective price at this instant. {@code alreadyEnrolled} and {@code ownPublisher} let it refuse a
 * duplicate purchase and a self-purchase without another call.
 */
public record PurchaseInfoResponse(
        long courseId,
        String title,
        BigDecimal price,
        BigDecimal effectivePrice,
        String currency,
        boolean discountActive,
        Instant discountEndsAt,
        String publisherName,
        boolean alreadyEnrolled,
        boolean ownPublisher,
        boolean free) {
}
