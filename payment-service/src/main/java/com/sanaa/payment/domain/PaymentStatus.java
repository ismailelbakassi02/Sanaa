package com.sanaa.payment.domain;

/**
 * Payment lifecycle. A payment starts PENDING_REVIEW and moves exactly once to APPROVED or
 * REJECTED; only PENDING_REVIEW rows can be reviewed.
 */
public enum PaymentStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
