package com.sanaa.gateway.ratelimit;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves the client IP used by the rate limit policies.
 *
 * <p>WHY maxTrustedIndex(1): the gateway sits behind exactly one proxy (nginx), which overwrites
 * {@code X-Forwarded-For} with {@code $remote_addr}. Trusting only the last hop means a client
 * cannot pick an arbitrary address by sending its own header: a request that arrives at the
 * gateway directly can still forge the header, so this is only safe when the gateway is not
 * reachable from outside the trusted network.
 *
 * <p>The result is never empty. When no address can be determined, the constant {@code unknown}
 * is used, so all such requests share one bucket (fail closed, never unlimited).
 */
@Component
public class ClientIpResolver {

    static final String UNKNOWN = "unknown";

    private final XForwardedRemoteAddressResolver remoteAddressResolver =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress address = remoteAddressResolver.resolve(exchange);
        if (address == null) {
            return UNKNOWN;
        }
        String host = address.getHostString();
        return host == null || host.isBlank() ? UNKNOWN : host;
    }
}
