package com.sanaa.auth.web.dto;

import java.time.Instant;

/**
 * Admin user row. Never includes the password hash.
 */
public record AdminUserSummary(
        long id,
        String email,
        String fullName,
        String platformRole,
        String status,
        boolean mustChangePassword,
        String tenantId,
        String tenantSlug,
        String publisherRole,
        Instant createdAt) {
}
