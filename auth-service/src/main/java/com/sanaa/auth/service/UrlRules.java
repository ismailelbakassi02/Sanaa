package com.sanaa.auth.service;

import java.net.URI;
import java.net.URISyntaxException;

import com.sanaa.auth.error.BadRequestException;

/**
 * Validation for the profile and publisher links.
 *
 * <p>WHY HTTPS only and no user info: these values are rendered as clickable links on public pages.
 * Allowing {@code javascript:} or {@code https://user:pass@host} would create an injection or a
 * credential leak. The stored value is the raw caller string, so the UI can show exactly what the
 * publisher typed; the scheme is enforced here.
 */
public final class UrlRules {

    public static final int MAX_LENGTH = 200;

    private UrlRules() {
    }

    /**
     * @return the trimmed value, or {@code null} when the caller sent blank.
     */
    public static String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String value = rawUrl.trim();
        if (value.length() > MAX_LENGTH) {
            throw new BadRequestException("Links must be at most 200 characters.");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new BadRequestException("Link is not a valid URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BadRequestException("Links must use https.");
        }
        if (uri.getUserInfo() != null) {
            throw new BadRequestException("Links must not contain credentials.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("Link must include a host.");
        }
        return value;
    }
}
