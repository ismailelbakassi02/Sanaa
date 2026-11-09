package com.sanaa.gateway.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Replaces the default empty 401 with an RFC 7807 body.
 */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    public JsonAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge(exception));
        return writer.write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "A valid access token is required.");
    }

    /**
     * Keeps the RFC 6750 error code on the challenge while omitting the description, which can
     * contain decoder internals (for example the JWKS URL or parser details).
     */
    private static String challenge(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2
                && oauth2.getError() != null
                && StringUtils.hasText(oauth2.getError().getErrorCode())) {
            return "Bearer error=\"" + oauth2.getError().getErrorCode() + "\"";
        }
        return "Bearer";
    }
}
