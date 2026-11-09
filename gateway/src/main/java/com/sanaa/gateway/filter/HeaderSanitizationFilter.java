package com.sanaa.gateway.filter;

import java.util.List;
import java.util.Locale;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Removes client-supplied tenant and user headers from every routed request, public or not.
 *
 * <p>WHY: downstream services must never see an identity header a client could spoof. Tenant and
 * user identity comes only from the validated JWT, and services read it from claims, not headers.
 * The Authorization header is deliberately left untouched so each service validates it again.
 */
@Component
public class HeaderSanitizationFilter implements GlobalFilter, Ordered {

    private static final List<String> FORBIDDEN_PREFIXES = List.of("x-tenant", "x-user");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Spring Framework 7 HttpHeaders no longer implements MultiValueMap, so the
                    // names come from headerNames() instead of keySet().
                    List<String> toRemove = headers.headerNames().stream()
                            .filter(HeaderSanitizationFilter::isForbidden)
                            .toList();
                    toRemove.forEach(headers::remove);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean isForbidden(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return FORBIDDEN_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    @Override
    public int getOrder() {
        // Run before the routing filters but after Spring Security has authenticated the request.
        return -3;
    }
}
