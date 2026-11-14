package com.sanaa.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * Blocks every authenticated endpoint while {@code must_change_password} is true, except the
 * password change and logout.
 *
 * <p>WHY a shared servlet filter: a temporary password is a restricted session, not a per-controller
 * rule, and every service validates the same token. Registering the filter opt-in in a service's
 * security chain (after bearer authentication) guarantees that a new endpoint cannot forget the
 * restriction. The gateway enforces the same rule first; this one protects a service when it is
 * called directly (tests, internal tooling, a misconfigured proxy).
 *
 * <p>WHY the rule is the same everywhere: only {@code POST /api/auth/password} and
 * {@code POST /api/auth/logout} are allowed. Services that do not expose the auth endpoints simply
 * let the session log out; they must never invent a third exception.
 *
 * <p>The body is an RFC 7807 problem with exactly five non-null fields, so it matches the problems
 * written by the controller advice and by the gateway.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    public static final String PASSWORD_PATH = "/api/auth/password";
    public static final String LOGOUT_PATH = "/api/auth/logout";

    /** JsonMapper is thread-safe, so one instance serves every request. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && Boolean.TRUE.equals(jwtAuthentication.getToken()
                        .getClaim(JwtClaims.MUST_CHANGE_PASSWORD))
                && !allowed(request)) {
            writeForbidden(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeForbidden(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String instance = request.getRequestURI().substring(request.getContextPath().length());
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Forbidden");
        problem.put("status", HttpStatus.FORBIDDEN.value());
        problem.put("detail", "Password change required.");
        problem.put("instance", instance);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON.writeValue(response.getOutputStream(), problem);
    }

    private static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod())
                && (PASSWORD_PATH.equals(path) || LOGOUT_PATH.equals(path));
    }
}
