package com.sanaa.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Policy A key: the client IP.
 *
 * <p>WHY this bean is {@link Primary}: the gateway auto-configuration injects a single
 * {@code KeyResolver} as the default. Routes override it with a SpEL expression, but the default
 * still has to be unambiguous.
 */
@Component
@Primary
public class ClientIpKeyResolver implements KeyResolver {

    private final ClientIpResolver clientIpResolver;

    public ClientIpKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just("ip:" + clientIpResolver.resolve(exchange));
    }
}
