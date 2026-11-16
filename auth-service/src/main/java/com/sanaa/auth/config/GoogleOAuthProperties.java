package com.sanaa.auth.config;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Google OpenID Connect settings. Credentials come only from environment variables; disabling the
 * feature leaves normal email/password authentication fully operational.
 */
@Validated
@ConfigurationProperties(prefix = "sanaa.oauth.google")
public record GoogleOAuthProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        @NotNull URI redirectUri,
        @NotNull URI clientBaseUrl,
        @NotNull Duration linkTicketTtl) {

    public GoogleOAuthProperties {
        requireHttpUri(redirectUri, "redirect-uri", false);
        requireHttpUri(clientBaseUrl, "client-base-url", true);
        if (linkTicketTtl.isNegative() || linkTicketTtl.isZero()
                || linkTicketTtl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("Google link-ticket-ttl must be between 1 second and 30 minutes.");
        }
        if (enabled && (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret))) {
            throw new IllegalArgumentException(
                    "Google OAuth is enabled but GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET is missing.");
        }
    }

    /** Builds a trusted frontend redirect; no request parameter can select another origin. */
    public URI clientUri(String pathAndQuery) {
        String base = clientBaseUrl.toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + pathAndQuery);
    }

    private static void requireHttpUri(URI uri, String property, boolean originOnly) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("Google " + property + " must be an HTTP(S) URI without credentials, query or fragment.");
        }
        if (originOnly && StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath())) {
            throw new IllegalArgumentException("Google client-base-url must contain only an origin.");
        }
    }
}
