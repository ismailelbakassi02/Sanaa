package com.sanaa.auth.service;

import org.springframework.util.StringUtils;

/** Verified claims extracted from Google's signed OpenID Connect ID token. */
public record GoogleProfile(String subject, String email, String fullName, boolean emailVerified) {

    private static final int SUBJECT_MAX = 255;
    private static final int NAME_MAX = 120;

    public GoogleProfile {
        if (!StringUtils.hasText(subject) || subject.length() > SUBJECT_MAX
                || !StringUtils.hasText(email) || email.length() > 255
                || !StringUtils.hasText(fullName)) {
            throw new IllegalArgumentException("Required Google identity claims are missing.");
        }
        subject = subject.trim();
        email = EmailNormalizer.normalize(email);
        fullName = truncate(fullName.trim(), NAME_MAX);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }
}
