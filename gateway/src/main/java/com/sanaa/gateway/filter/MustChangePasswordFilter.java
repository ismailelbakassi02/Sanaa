package com.sanaa.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.gateway.error.ProblemDetailWriter;

import reactor.core.publisher.Mono;

/**
 * A session created with a temporary password may only change the password or log out.
 *
 * <p>The rule lives at the edge so a forgot-to-check controller cannot be reached. auth-service
 * enforces the same rule again when it is called directly.
 */
@Component
public class MustChangePasswordFilter implements GlobalFilter, Ordered {

    private static final String PASSWORD_PATH = "/api/auth/password";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final ProblemDetailWriter writer;

    public MustChangePasswordFilter(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (allowed(exchange)) {
            return chain.filter(exchange);
        }

        Mono<Boolean> restricted = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> Boolean.TRUE.equals(
                        token.getToken().getClaim(JwtClaims.MUST_CHANGE_PASSWORD)))
                .defaultIfEmpty(false);

        return restricted.flatMap(blocked -> blocked
                ? writer.write(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                        "Password change required.")
                : chain.filter(exchange));
    }

    private static boolean allowed(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        return HttpMethod.POST.equals(method)
                && (PASSWORD_PATH.equals(path) || LOGOUT_PATH.equals(path));
    }

    @Override
    public int getOrder() {
        // After authentication, before the rate limiter and the routing filters.
        return -2;
    }
}
