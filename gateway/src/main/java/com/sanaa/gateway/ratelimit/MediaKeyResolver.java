package com.sanaa.gateway.ratelimit;

import java.util.List;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Policy C key: client IP plus the lesson id from the path.
 *
 * <p>WHY per lesson: one student who skips through a playlist must not exhaust the budget for the
 * other lessons, and a single hot lesson must not be able to consume the whole IP budget. The
 * lesson id is the first segment after {@code /api/stream/media/}.
 */
@Component
public class MediaKeyResolver implements KeyResolver {

    private static final String MEDIA_PREFIX = "/api/stream/media/";

    private final ClientIpResolver clientIpResolver;

    public MediaKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return Mono.just("media:" + clientIpResolver.resolve(exchange) + ":" + lessonId(path));
    }

    /**
     * Extracts the lesson id, or a stable fallback so the key is never empty.
     */
    static String lessonId(String path) {
        int start = path.indexOf(MEDIA_PREFIX);
        if (start < 0) {
            return "unknown";
        }
        String rest = path.substring(start + MEDIA_PREFIX.length());
        List<String> segments = java.util.Arrays.stream(rest.split("/"))
                .filter(segment -> !segment.isEmpty())
                .toList();
        return segments.isEmpty() ? "unknown" : segments.getFirst();
    }
}
