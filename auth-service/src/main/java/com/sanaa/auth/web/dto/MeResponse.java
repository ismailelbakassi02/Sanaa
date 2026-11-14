package com.sanaa.auth.web.dto;

/**
 * Current identity. Tenant fields are null for learners and SUPER_ADMIN, who have no publisher.
 */
public record MeResponse(
        long id,
        String email,
        String fullName,
        String role,
        String tenantId,
        String tenantSlug,
        String tenantType,
        boolean mustChangePassword) {
}
