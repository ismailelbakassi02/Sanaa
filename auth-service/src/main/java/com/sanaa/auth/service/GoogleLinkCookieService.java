package com.sanaa.auth.service;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.sanaa.auth.config.AuthProperties;
import com.sanaa.auth.config.GoogleOAuthProperties;

/** HttpOnly transport for the short-lived account-link ticket. */
@Service
public class GoogleLinkCookieService {

    public static final String COOKIE_NAME = "sanaa_google_link";
    public static final String COOKIE_PATH = "/api/auth/oauth2/google/link";

    private final AuthProperties authProperties;
    private final GoogleOAuthProperties googleProperties;

    public GoogleLinkCookieService(AuthProperties authProperties,
                                   GoogleOAuthProperties googleProperties) {
        this.authProperties = authProperties;
        this.googleProperties = googleProperties;
    }

    public String issue(String rawTicket) {
        return cookie(rawTicket, googleProperties.linkTicketTtl()).toString();
    }

    public String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
