package com.sanaa.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.sanaa.auth.config.JwtProperties;
import com.sanaa.auth.web.dto.LoginResponse;
import com.sanaa.common.security.JwtClaims;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public LoginResponse issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(Long.toString(user.id()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(JwtClaims.TENANT_ID, user.tenantId())
                .claim(JwtClaims.TENANT_SLUG, user.tenantSlug())
                .claim(JwtClaims.ROLE, user.role().name())
                .claim(JwtClaims.EMAIL, user.email())
                .claim(JwtClaims.NAME, user.fullName())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, jwtProperties.accessTokenTtl().toSeconds());
    }
}
