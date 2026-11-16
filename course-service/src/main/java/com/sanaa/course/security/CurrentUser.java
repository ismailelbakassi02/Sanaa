package com.sanaa.course.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.common.security.Role;
import com.sanaa.course.error.UnauthenticatedException;

/**
 * The caller identity taken only from validated JWT claims. The tenant is handled separately by
 * {@code TenantContext} and is never accepted from the request.
 *
 * <p>The display name and the publisher slug/type are only used to create the cross-tenant
 * placeholder snapshots when a studio creates its first course before the publisher event has
 * arrived. They are never trusted for authorization.
 */
public record CurrentUser(long userId, Role role, String email, String name, String tenantSlug,
                          String tenantType) {

    public static CurrentUser from(Jwt jwt) {
        String subject = jwt.getSubject();
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        if (subject == null || role == null) {
            throw new UnauthenticatedException("The access token is missing required claims.");
        }
        try {
            return new CurrentUser(Long.parseLong(subject), Role.valueOf(role),
                    jwt.getClaimAsString(JwtClaims.EMAIL),
                    jwt.getClaimAsString(JwtClaims.NAME),
                    jwt.getClaimAsString(JwtClaims.TENANT_SLUG),
                    jwt.getClaimAsString(JwtClaims.TENANT_TYPE));
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedException("The access token subject or role is invalid.");
        }
    }
}
