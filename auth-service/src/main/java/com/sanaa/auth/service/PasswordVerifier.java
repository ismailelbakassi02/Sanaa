package com.sanaa.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt comparison with a dummy hash for absent users or tenants.
 *
 * <p>WHY: returning early when the user does not exist would make "unknown email" measurably faster
 * than "wrong password" and allow account enumeration. Every login path performs one BCrypt
 * comparison against either the real hash or this process-local dummy hash.
 */
@Component
public class PasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public PasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        // The plaintext is random and discarded; only the hash is kept for timing equalization.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    public void performDummyVerification(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyHash);
    }
}
