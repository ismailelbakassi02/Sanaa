package com.sanaa.course.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.common.security.Role;
import com.sanaa.course.error.UnauthenticatedException;

/**
 * The caller identity taken only from validated JWT claims. The tenant is handled separately by
 * {@code TenantContext} and is never accepted from the request.
 */
public record CurrentUser(long userId, Role role) {

    public static CurrentUser from(Jwt jwt) {
        String subject = jwt.getSubject();
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        if (subject == null || role == null) {
            throw new UnauthenticatedException("The access token is missing required claims.");
        }
        try {
            return new CurrentUser(Long.parseLong(subject), Role.valueOf(role));
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedException("The access token subject or role is invalid.");
        }
    }
}
