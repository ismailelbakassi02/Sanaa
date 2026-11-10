package com.sanaa.payment.service;

/**
 * Receipt bytes plus the verified content type stored when the payment was created.
 */
public record ReceiptFile(byte[] content, String contentType) {
}
