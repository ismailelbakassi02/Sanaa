package com.sanaa.auth.service;

import java.nio.charset.StandardCharsets;

import com.sanaa.auth.error.BadRequestException;

/**
 * The single password rule, applied everywhere a password is set (register, publisher owner,
 * team member, password change, team reset).
 *
 * <p>WHY bytes and not characters: BCrypt truncates at 72 bytes, so a 40-character emoji password
 * would silently lose entropy if only characters were counted. The API reports the rule in English
 * because it is a machine-facing detail, not French UI copy.
 */
public final class PasswordPolicy {

    public static final int MIN_BYTES = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null) {
            throw new BadRequestException("Password is required.");
        }
        int bytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_BYTES || bytes > MAX_BYTES) {
            throw new BadRequestException(
                    "Password must be between 10 and 72 bytes in UTF-8.");
        }
    }
}
