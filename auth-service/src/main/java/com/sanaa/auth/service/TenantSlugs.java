package com.sanaa.auth.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tenant slug rules shared by registration and platform tenant creation.
 *
 * <p>Why a reserved list: these labels are used by infrastructure, the API or the platform itself.
 * Allowing a tenant to claim them would make routing and platform administration ambiguous. The
 * platform tenant is reserved as well, so public registration can never add users to it.
 */
public final class TenantSlugs {

    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9-]{3,63}$");
    public static final String PLATFORM = "platform";

    private static final Set<String> RESERVED = Set.of(
            PLATFORM, "www", "api", "admin", "auth", "app", "static", "mail");

    private TenantSlugs() {
    }

    public static String normalize(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String slug) {
        return slug != null && PATTERN.matcher(slug).matches();
    }

    public static boolean isReserved(String slug) {
        return slug != null && RESERVED.contains(slug);
    }
}
