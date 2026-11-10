package com.sanaa.payment.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.sanaa.payment.domain.Payment;

/**
 * Payment representation returned by the API. The receipt path is deliberately absent: clients
 * download the file through the dedicated endpoint and never see the storage layout.
 */
public record PaymentResponse(
        Long id,
        Long userId,
        String userEmail,
        String userName,
        Long courseId,
        String courseTitle,
        BigDecimal amount,
        String currency,
        String method,
        String status,
        String receiptContentType,
        Long reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getUserEmail(),
                payment.getUserName(),
                payment.getCourseId(),
                payment.getCourseTitle(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod().name(),
                payment.getStatus().name(),
                payment.getReceiptContentType(),
                payment.getReviewedBy(),
                payment.getReviewedAt(),
                payment.getRejectionReason(),
                payment.getVersion(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
