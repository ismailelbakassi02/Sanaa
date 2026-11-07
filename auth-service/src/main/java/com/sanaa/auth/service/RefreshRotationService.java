package com.sanaa.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.config.AuthProperties;
import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.domain.UserStatus;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;

/**
 * Rotates a presented refresh token.
 *
 * <p>The caller (the non-transactional facade) has already set {@link com.sanaa.common.tenant.TenantContext}
 * from the token row, so the user lookup and the new token row are tenant-safe. The conditional
 * revoke is the serialization point for concurrent refreshes: exactly one transaction can change
 * {@code revoked_at} from NULL, and the loser is reported as reuse.
 */
@Service
public class RefreshRotationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenCodec codec;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshRotationService(RefreshTokenRepository refreshTokenRepository,
                                  TenantRepository tenantRepository,
                                  UserRepository userRepository,
                                  RefreshTokenCodec codec,
                                  AuthProperties properties,
                                  Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshRotation rotate(RefreshToken presented) {
        Instant now = clock.instant();

        Optional<Tenant> tenant = tenantRepository.findById(presented.getTenantId())
                .filter(found -> found.getStatus() == TenantStatus.ACTIVE);
        if (tenant.isEmpty()) {
            throw new UnauthenticatedException();
        }

        Optional<User> user = userRepository.findById(presented.getUserId())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE);
        if (user.isEmpty()) {
            throw new UnauthenticatedException();
        }

        int updated = refreshTokenRepository.revokeIfActive(presented.getId(), now);
        if (updated != 1) {
            return RefreshRotation.reuseDetected();
        }

        String rawToken = codec.generate();
        refreshTokenRepository.save(new RefreshToken(tenant.get().getId(), user.get().getId(),
                codec.hash(rawToken), now.plus(properties.refreshTokenTtl())));

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.get().getId(),
                tenant.get().getId(), tenant.get().getSlug(), user.get().getEmail(),
                user.get().getFullName(), user.get().getRole());
        return RefreshRotation.success(authenticatedUser, rawToken);
    }
}
