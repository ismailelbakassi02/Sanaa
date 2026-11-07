package com.sanaa.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates opaque refresh tokens and hashes them for storage.
 *
 * <p>WHY 32 random bytes: the token is a bearer credential, so it must have at least 256 bits of
 * entropy. The database only ever sees the SHA-256 hex digest; a database leak does not expose a
 * usable token.
 */
@Component
public class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the Java platform; this cannot happen on a supported JDK.
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
