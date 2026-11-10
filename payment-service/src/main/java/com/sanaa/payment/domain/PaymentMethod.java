package com.sanaa.payment.domain;

/**
 * Payment method chosen by the student. The receipt is uploaded for both methods; only the
 * bookkeeping differs.
 */
public enum PaymentMethod {
    BANK_WIRE,
    CASHPLUS
}
