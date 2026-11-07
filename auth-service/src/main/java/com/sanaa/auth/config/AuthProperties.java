package com.sanaa.auth.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Refresh-session settings. The cookie is Secure by default outside the dev profile; the dev
 * profile overrides that because local traffic is plain HTTP.
 */
@Validated
@ConfigurationProperties(prefix = "sanaa.auth")
public record AuthProperties(
        boolean refreshCookieSecure,
        @NotNull Duration refreshTokenTtl) {
}
