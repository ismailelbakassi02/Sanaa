package com.sanaa.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter =
            new SecurityConfig().jwtAuthenticationConverter();

    @Test
    void mapsTheRoleClaimToARolePrefixedAuthority() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("42")
                .claim("role", "STUDENT")
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STUDENT");
    }
}
