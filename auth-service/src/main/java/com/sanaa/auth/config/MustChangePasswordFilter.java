package com.sanaa.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sanaa.common.security.JwtClaims;

/**
 * Blocks every authenticated endpoint while {@code must_change_password} is true, except the
 * password change and logout.
 *
 * <p>WHY a filter and not a method annotation: a temporary password is a restricted session, not a
 * per-controller rule. Doing it here guarantees that a new endpoint cannot forget the restriction.
 * The gateway enforces the same rule first; this one protects the service when it is called
 * directly (tests, internal tooling).
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final String PASSWORD_PATH = "/api/auth/password";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && Boolean.TRUE.equals(jwtAuthentication.getToken()
                        .getClaim(JwtClaims.MUST_CHANGE_PASSWORD))
                && !allowed(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            new tools.jackson.databind.json.JsonMapper().writeValue(response.getOutputStream(),
                    Map.of("type", "about:blank", "title", "Forbidden",
                            "status", HttpStatus.FORBIDDEN.value(),
                            "detail", "Password change required."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod())
                && (PASSWORD_PATH.equals(path) || LOGOUT_PATH.equals(path));
    }
}
