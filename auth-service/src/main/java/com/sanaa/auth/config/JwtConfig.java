package com.sanaa.auth.config;

import java.time.Clock;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    public JwtKeyProvider jwtKeyProvider(JwtProperties properties) {
        return JwtKeyProvider.load(properties.privateKeyPath(), properties.publicKeyPath());
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        JWKSource<SecurityContext> source =
                new ImmutableJWKSet<>(new JWKSet(keyProvider.signingKey()));
        return new NimbusJwtEncoder(source);
    }

    /**
     * Auth-service validates its own tokens locally with the public key. No HTTP call to itself and
     * no shared secret are involved.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyProvider.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(properties.audience()));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
