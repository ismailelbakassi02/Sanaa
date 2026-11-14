package com.sanaa.auth.service;

import com.sanaa.auth.domain.TenantType;
import com.sanaa.common.security.Role;

/**
 * The minimal user data needed to issue a token and answer {@code /me}. It is a value object so no
 * JPA entity crosses the transaction boundary into the web layer. Tenant fields are null for
 * learners and SUPER_ADMIN.
 */
public record AuthenticatedUser(
        long id,
        String tenantId,
        String tenantSlug,
        TenantType tenantType,
        String email,
        String fullName,
        Role role,
        boolean mustChangePassword) {
}
