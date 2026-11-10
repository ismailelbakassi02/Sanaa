package com.sanaa.payment.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.common.security.Role;
import com.sanaa.payment.error.UnauthenticatedException;

/**
 * The caller identity taken only from validated JWT claims. The tenant is still applied to
 * Hibernate by {@code TenantContext}; this record carries it for the event envelope and for
 * ownership checks.
 */
public record CurrentUser(long userId, String tenantId, Role role, String email, String name) {

    public static CurrentUser from(Jwt jwt) {
        String subject = jwt.getSubject();
        String tenantId = jwt.getClaimAsString(JwtClaims.TENANT_ID);
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        String email = jwt.getClaimAsString(JwtClaims.EMAIL);
        String name = jwt.getClaimAsString(JwtClaims.NAME);
        if (subject == null || tenantId == null || role == null || email == null || name == null) {
            throw new UnauthenticatedException("The access token is missing required claims.");
        }
        try {
            return new CurrentUser(Long.parseLong(subject), tenantId, Role.valueOf(role),
                    email, name);
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedException("The access token subject or role is invalid.");
        }
    }
}
