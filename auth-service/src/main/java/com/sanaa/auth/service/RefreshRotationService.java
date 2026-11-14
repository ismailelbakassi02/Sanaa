package com.sanaa.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.config.AuthProperties;
import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.UserRepository;

/**
 * Rotates a presented refresh token and re-evaluates the claims.
 *
 * <p>WHY no tenant context is needed here: users, refresh tokens and the read-only membership view
 * are global tables. The previous v1 code set the context from the token row; v2 resolves the
 * publisher from the current membership instead, so a suspension is honoured instead of failing
 * with 401.
 *
 * <p>The conditional revoke is the serialization point for concurrent refreshes: exactly one
 * transaction can change {@code revoked_at} from NULL.
 */
@Service
public class RefreshRotationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final ClaimsService claimsService;
    private final RefreshTokenCodec codec;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshRotationService(RefreshTokenRepository refreshTokenRepository,
                                  UserRepository userRepository,
                                  ClaimsService claimsService,
                                  RefreshTokenCodec codec,
                                  AuthProperties properties,
                                  Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.claimsService = claimsService;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshRotation rotate(RefreshToken presented) {
        Instant now = clock.instant();

        Optional<User> found = userRepository.findById(presented.getUserId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            // A disabled account cannot extend its session, even with a valid cookie.
            throw new UnauthenticatedException();
        }

        int updated = refreshTokenRepository.revokeIfActive(presented.getId(), now);
        if (updated != 1) {
            return RefreshRotation.reuseDetected();
        }

        AuthenticatedUser user = claimsService.resolve(found.get());
        String rawToken = codec.generate();
        refreshTokenRepository.save(new RefreshToken(user.tenantId(), user.id(),
                codec.hash(rawToken), now.plus(properties.refreshTokenTtl())));
        return RefreshRotation.success(user, rawToken);
    }
}
