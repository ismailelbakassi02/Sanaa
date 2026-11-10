package com.sanaa.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server settings. The JWKS URI is only contacted on the first token validation, so
 * payment-service can start before auth-service is available.
 */
@ConfigurationProperties(prefix = "sanaa.jwt")
public record JwtProperties(
        String jwksUri,
        String issuer,
        String audience) {
}
