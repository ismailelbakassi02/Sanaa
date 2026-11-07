package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.repository.RefreshTokenRepository;

/**
 * Reads a refresh token by hash without a tenant context. The caller must set the tenant from the
 * returned row before it opens any tenant-owned transaction.
 */
@Service
public class RefreshTokenLookupService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenLookupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
