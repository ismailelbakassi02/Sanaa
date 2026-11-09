package com.sanaa.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.sanaa.common.security.JwtClaims;

import reactor.core.publisher.Mono;

/**
 * Policy B key: {@code tenant_id:sub} from the validated token.
 *
 * <p>WHY the token and not a header: the identity must come from the same claims the downstream
 * services trust, otherwise a client could move its own traffic into another user's bucket. The
 * Redis key also contains the route id, so each route group has its own bucket for the same user;
 * the policy limit is therefore per user per group, not a global budget.
 *
 * <p>The unauthenticated fallback is the client IP, so a request that reaches the limiter without
 * a JWT is still counted instead of receiving an empty key.
 */
@Component
public class TenantUserKeyResolver implements KeyResolver {

    private final ClientIpResolver clientIpResolver;

    public TenantUserKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> "user:" + tenantId(token) + ":" + subject(token))
                .defaultIfEmpty("ip:" + clientIpResolver.resolve(exchange));
    }

    private static String tenantId(JwtAuthenticationToken token) {
        String tenantId = token.getToken().getClaimAsString(JwtClaims.TENANT_ID);
        // Fail closed: tokens without a tenant claim still share one bucket per subject.
        return tenantId == null || tenantId.isBlank() ? "none" : tenantId;
    }

    private static String subject(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        return subject == null || subject.isBlank() ? "anonymous" : subject;
    }
}
