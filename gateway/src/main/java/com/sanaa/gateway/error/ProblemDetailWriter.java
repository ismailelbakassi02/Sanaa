package com.sanaa.gateway.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.annotation.JsonInclude;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes RFC 7807 responses for the gateway's own rejections.
 *
 * <p>WHY a separate writer: the gateway has no controller advice (it does not route to local
 * controllers for API paths), so the security handlers and global filters share one serialization
 * path and one response shape.
 */
@Component
public class ProblemDetailWriter {

    // Matches spring.jackson.default-property-inclusion: ProblemDetail has nullable type and
    // properties, and an error body should not carry explicit nulls.
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion ->
                    inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String title,
                            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

        byte[] body = jsonMapper.writeValueAsBytes(problem);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
