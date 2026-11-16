package com.sanaa.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/** Transient-session chain used only for the Google authorization request, state and nonce. */
@Configuration
@ConditionalOnProperty(prefix = "sanaa.oauth.google", name = "enabled", havingValue = "true")
public class GoogleOAuthSecurityConfig {

    @Bean
    public ClientRegistrationRepository googleClientRegistrationRepository(
            GoogleOAuthProperties properties) {
        ClientRegistration registration = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .redirectUri(properties.redirectUri().toString())
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain googleOAuthSecurityFilterChain(
            HttpSecurity http,
            GoogleOAuthSuccessHandler successHandler,
            GoogleOAuthFailureHandler failureHandler) throws Exception {
        http
                .securityMatcher("/api/auth/oauth2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/api/auth/oauth2/authorization"))
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/api/auth/oauth2/callback/*"))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
