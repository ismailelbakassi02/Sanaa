package com.sanaa.auth.service;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.sanaa.auth.config.AuthProperties;

/**
 * Builds the refresh cookie. SameSite=Strict is the CSRF defence for the cookie-bearing endpoints;
 * the token API itself is stateless and therefore disables CSRF.
 */
@Service
public class RefreshCookieService {

    public static final String COOKIE_NAME = "sanaa_refresh";
    public static final String COOKIE_PATH = "/api/auth";

    private final AuthProperties properties;

    public RefreshCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public String issue(String rawToken) {
        return cookie(rawToken, properties.refreshTokenTtl()).toString();
    }

    public String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
