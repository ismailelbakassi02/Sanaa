package com.sanaa.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Payment-service settings. The course URL is the only source of the amount, title and currency,
 * and the receipts directory is the root of the local {@code ReceiptStorage}.
 */
@ConfigurationProperties(prefix = "sanaa.payment")
public record PaymentProperties(
        String courseServiceUrl,
        String receiptsDir) {
}
