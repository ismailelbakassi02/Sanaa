package com.sanaa.gateway.filter;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.gateway.error.ProblemDetailWriter;

import reactor.core.publisher.Mono;

/**
 * Rejects an authenticated request when the Host subdomain and the token's tenant_slug disagree.
 *
 * <p>WHY: the SPA selects a tenant for display from the hostname. A token issued for tenant A must
 * not be usable on tenant B's hostname, even though the token itself is valid. This check is a
 * defence in depth only: authorization still comes from the token, and a direct IP client can
 * choose any Host header. nginx must therefore send {@code proxy_set_header Host $host} so the
 * browser-visible host reaches the gateway unchanged.
 */
@Component
public class HostTenantFilter implements GlobalFilter, Ordered {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final String baseDomain;
    private final ProblemDetailWriter writer;

    public HostTenantFilter(
            @Value("${sanaa.gateway.base-domain:localhost}") String baseDomain,
            ProblemDetailWriter writer) {
        this.baseDomain = baseDomain.toLowerCase();
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String subdomain = subdomainOf(exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
        if (subdomain == null) {
            return chain.filter(exchange);
        }

        // defaultIfEmpty(true): public or unauthenticated requests skip the tenant comparison.
        Mono<Boolean> matches = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> subdomain.equals(
                        token.getToken().getClaimAsString(JwtClaims.TENANT_SLUG)))
                .defaultIfEmpty(true);

        return matches.flatMap(allowed -> allowed
                ? chain.filter(exchange)
                : writer.write(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                        "The Host tenant does not match the access token."));
    }

    /**
     * Returns the single label before the configured base domain, or null for hosts that must skip
     * the check: no Host header, the base domain itself, localhost, or a plain IP address.
     */
    String subdomainOf(String rawHost) {
        if (!StringUtils.hasText(rawHost)) {
            return null;
        }
        String host = stripPort(rawHost.trim()).toLowerCase();
        if (host.equals("localhost") || host.equals(baseDomain) || isIpLiteral(host)) {
            return null;
        }
        String suffix = "." + baseDomain;
        if (!host.endsWith(suffix)) {
            return null;
        }
        String prefix = host.substring(0, host.length() - suffix.length());
        // A nested name such as acme.eu.localhost is compared as a whole and cannot equal a
        // single-label tenant slug, so it fails closed rather than skipping the check.
        return prefix.isEmpty() ? null : prefix;
    }

    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            return host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    private static boolean isIpLiteral(String host) {
        return IPV4.matcher(host).matches() || host.contains(":") || host.startsWith("[");
    }

    @Override
    public int getOrder() {
        // After authentication, before the RequestRateLimiter route filters.
        return -2;
    }
}
