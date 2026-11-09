package com.sanaa.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Replaces the default empty 403 with an RFC 7807 body.
 */
@Component
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ProblemDetailWriter writer;

    public JsonAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writer.write(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                "The access token does not allow this request.");
    }
}
