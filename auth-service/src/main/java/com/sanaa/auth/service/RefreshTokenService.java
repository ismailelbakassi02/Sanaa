package com.sanaa.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.config.AuthProperties;
import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.repository.RefreshTokenRepository;

/**
 * Creates and revokes refresh-token rows. Rotation is in {@link RefreshRotationService} because it
 * also has to re-check the user and tenant before it writes.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec codec;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenCodec codec,
                               AuthProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(String tenantId, long userId) {
        String rawToken = codec.generate();
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(tenantId, userId, codec.hash(rawToken), expiresAt));
        return rawToken;
    }

    @Transactional
    public void revokeById(long tokenId) {
        refreshTokenRepository.revokeIfActive(tokenId, clock.instant());
    }

    @Transactional
    public void revokeAllForUser(long userId) {
        refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    @Transactional
    public void revokeAllForTenant(String tenantId) {
        refreshTokenRepository.revokeAllForTenant(tenantId, clock.instant());
    }
}
