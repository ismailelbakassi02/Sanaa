package com.sanaa.auth.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.sanaa.auth.error.BadRequestException;

/**
 * Publisher slug rules. A slug is permanent: it appears in the public URL and in events, so it is
 * validated once at creation and never recomputed.
 *
 * <p>The reserved list protects infrastructure hostnames and the routes the platform itself owns.
 * {@code xn--} is rejected because it is a punycode prefix and would allow lookalike domains.
 */
public final class SlugRules {

    public static final Pattern PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$");

    private static final Set<String> RESERVED = Set.of(
            "platform", "www", "api", "admin", "app", "mail", "static", "auth", "support", "help",
            "blog", "docs", "status", "cdn", "assets", "login", "register", "dashboard", "billing",
            "sanaa", "studio", "learn", "courses", "publishers", "instructors", "public");

    private SlugRules() {
    }

    public static String normalize(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws BadRequestException when the slug is malformed or reserved.
     */
    public static String requireValid(String slug) {
        String normalized = normalize(slug);
        if (normalized == null || !PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException(
                    "Slug must be 3 to 63 lowercase letters, digits or hyphens and start and end "
                            + "with a letter or digit.");
        }
        if (normalized.startsWith("xn--")) {
            throw new BadRequestException("Slug must not start with xn--.");
        }
        if (RESERVED.contains(normalized)) {
            throw new BadRequestException("Slug is reserved.");
        }
        return normalized;
    }

    public static boolean isValid(String slug) {
        return slug != null && PATTERN.matcher(slug).matches() && !slug.startsWith("xn--");
    }

    public static boolean isReserved(String slug) {
        return slug != null && RESERVED.contains(slug);
    }
}
