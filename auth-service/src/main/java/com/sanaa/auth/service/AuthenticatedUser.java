package com.sanaa.auth.service;

import com.sanaa.common.security.Role;

/**
 * The minimal user data needed to issue a token. It is a value object so no JPA entity crosses the
 * transaction boundary into the web layer.
 */
public record AuthenticatedUser(
        long id,
        String tenantId,
        String tenantSlug,
        String email,
        String fullName,
        Role role) {
}
