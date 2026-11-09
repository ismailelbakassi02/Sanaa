package com.sanaa.gateway.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.sanaa.common.security.JwtClaims;

/**
 * Generates a throwaway RSA key pair, the matching JWKS document and RS256 access tokens.
 *
 * <p>WHY: the gateway tests must not depend on auth-service. The public key is served from a stub
 * JWKS endpoint, so the production decoder configuration (jwk-set-uri, issuer, audience) is
 * exercised unchanged.
 */
public final class TestTokens {

    public static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    public static final String TENANT_SLUG = "atlas";

    private static final String KEY_ID = "gateway-test-key";

    private final RSASSASigner signer;
    private final String jwksJson;

    private TestTokens(RSASSASigner signer, String jwksJson) {
        this.signer = signer;
        this.jwksJson = jwksJson;
    }

    public static TestTokens generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();

            String jwks = new JWKSet(rsaKey).toPublicJWKSet().toString();
            return new TestTokens(new RSASSASigner(rsaKey), jwks);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create the test RSA key", exception);
        }
    }

    public String jwksJson() {
        return jwksJson;
    }

    /**
     * Builds and signs a token with the default claims, after applying the customizer.
     */
    public String token(Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(JwtClaims.ISSUER)
                .audience(JwtClaims.AUDIENCE_API)
                .subject("user-1")
                .claim(JwtClaims.TENANT_ID, TENANT_ID)
                .claim(JwtClaims.TENANT_SLUG, TENANT_SLUG)
                .claim(JwtClaims.ROLE, "STUDENT")
                .claim(JwtClaims.EMAIL, "user@atlas.local")
                .claim(JwtClaims.NAME, "Gateway Test User")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)));
        customizer.accept(claims);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(signer);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign the test token", exception);
        }
        return jwt.serialize();
    }

    public String validToken(String tenantSlug, String subject) {
        return token(claims -> claims
                .subject(subject)
                .claim(JwtClaims.TENANT_SLUG, tenantSlug));
    }
}
