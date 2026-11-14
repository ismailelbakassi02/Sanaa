package com.sanaa.gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import com.sanaa.common.security.JwtClaims;
import com.sanaa.gateway.error.JsonAccessDeniedHandler;
import com.sanaa.gateway.error.JsonAuthenticationEntryPoint;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * The decoder fetches the JWKS lazily on the first token, so the gateway starts while
     * auth-service is down. Ignoring the issuer URI for discovery avoids a startup call as well;
     * the issuer is enforced by the validator below.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${AUTH_JWKS_URI:http://localhost:8081/.well-known/jwks.json}") String jwksUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(JwtClaims.ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(JwtClaims.AUDIENCE_API));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    /**
     * The matcher is limited to routed API prefixes. An unknown path such as /api/notifications or
     * /actuator therefore falls through to the gateway's 404 instead of being turned into a 401.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/learner/**",
                        "/api/studio/**",
                        "/api/admin/**",
                        "/api/complaints/**",
                        "/api/courses/**",
                        "/api/modules/**",
                        "/api/lessons/**",
                        "/api/enrollments/**",
                        "/api/stream/**",
                        "/api/payments/**"))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                // Anonymous publisher creation with owner data, and the
                                // logged-in learner path, share this endpoint.
                                "/api/auth/publishers",
                                "/api/payments/card/callback").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/auth/publishers/slug-available",
                                "/api/public/**").permitAll()
                        .pathMatchers("/api/stream/media/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        // WHY also here: the resource server registers its own default entry point
                        // and handler that answer with an empty body. Without these two lines a
                        // request carrying an invalid or expired token would bypass the JSON
                        // ProblemDetail entry point below.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
