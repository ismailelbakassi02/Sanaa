package com.sanaa.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Policy B key: the token subject ({@code sub}).
 *
 * <p>WHY not {@code tenant_id:sub}: users are global in v2 and a member's tenant can change (a
 * suspended publisher downgrades to STUDENT). Keying by the stable user id keeps one bucket per
 * person across those transitions. The identity comes from the validated token, never a header.
 * The Redis key also contains the route id, so the budget is per user per route group.
 *
 * <p>The unauthenticated fallback is the client IP, so a request that reaches the limiter without a
 * JWT is counted instead of receiving an empty key.
 */
@Component
public class UserKeyResolver implements KeyResolver {

    private final ClientIpResolver clientIpResolver;

    public UserKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> "user:" + subject(token))
                .defaultIfEmpty("ip:" + clientIpResolver.resolve(exchange));
    }

    private static String subject(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        return subject == null || subject.isBlank() ? "anonymous" : subject;
    }
}
