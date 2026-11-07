package com.sanaa.auth.config;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The private and public key paths are supplied through environment variables; no
 * key material is stored in the repository.
 */
@Validated
@ConfigurationProperties(prefix = "sanaa.jwt")
public record JwtProperties(
        @NotNull Path privateKeyPath,
        @NotNull Path publicKeyPath,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl) {
}
