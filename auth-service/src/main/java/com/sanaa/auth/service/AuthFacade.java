package com.sanaa.auth.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.web.dto.ChangePasswordRequest;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.auth.web.dto.RegisterRequest;
import com.sanaa.auth.web.dto.RegisterResponse;
import com.sanaa.auth.web.dto.LoginRequest;

/**
 * Entry point for the unauthenticated identity flows.
 *
 * <p>None of these methods need a tenant context: users, refresh tokens and the read-only
 * membership view are global. The publisher is resolved by {@link ClaimsService} from the membership
 * and can disappear (suspension, disabled membership) without invalidating the session.
 */
@Service
public class AuthFacade {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final TokenService tokenService;
    private final UserQueryService userQueryService;
    private final RefreshTokenLookupService refreshTokenLookupService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshRotationService refreshRotationService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCodec refreshTokenCodec;
    private final Clock clock;

    public AuthFacade(RegistrationService registrationService,
                      LoginService loginService,
                      TokenService tokenService,
                      UserQueryService userQueryService,
                      RefreshTokenLookupService refreshTokenLookupService,
                      RefreshTokenService refreshTokenService,
                      RefreshRotationService refreshRotationService,
                      PasswordChangeService passwordChangeService,
                      RefreshTokenCodec refreshTokenCodec,
                      Clock clock) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.userQueryService = userQueryService;
        this.refreshTokenLookupService = refreshTokenLookupService;
        this.refreshTokenService = refreshTokenService;
        this.refreshRotationService = refreshRotationService;
        this.passwordChangeService = passwordChangeService;
        this.refreshTokenCodec = refreshTokenCodec;
        this.clock = clock;
    }

    public RegisterResponse register(RegisterRequest request) {
        AuthenticatedUser user = registrationService.register(request);
        return new RegisterResponse(user.id(), user.email(), user.fullName(), user.role().name());
    }

    public AuthResult login(LoginRequest request) {
        AuthenticatedUser user = loginService.login(EmailNormalizer.normalize(request.email()),
                request.password());
        String refreshToken = refreshTokenService.issue(user.tenantId(), user.id());
        return new AuthResult(tokenService.issue(user), refreshToken);
    }

    /**
     * Refreshes a session. Only the cookie is available, so the token row is found by hash first;
     * the account and its memberships are then re-read to rebuild the claims.
     */
    public AuthResult refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new UnauthenticatedException();
        }

        Optional<RefreshToken> found =
                refreshTokenLookupService.findByHash(refreshTokenCodec.hash(rawRefreshToken));
        if (found.isEmpty()) {
            throw new UnauthenticatedException();
        }
        RefreshToken presented = found.get();

        if (presented.isExpired(clock.instant())) {
            throw new UnauthenticatedException();
        }

        if (presented.isRevoked()) {
            revokeAllAfterReuse(presented);
            throw new UnauthenticatedException();
        }

        RefreshRotation rotation = refreshRotationService.rotate(presented);
        if (rotation.reused()) {
            revokeAllAfterReuse(presented);
            throw new UnauthenticatedException();
        }

        return new AuthResult(tokenService.issue(rotation.user()), rotation.rawRefreshToken());
    }

    /**
     * Revokes the presented token if it exists. The endpoint is public and always returns 204, so a
     * missing or already revoked cookie is not distinguishable from a successful logout.
     */
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenLookupService.findByHash(refreshTokenCodec.hash(rawRefreshToken))
                .ifPresent(token -> refreshTokenService.revokeById(token.getId()));
    }

    public MeResponse me(Jwt jwt) {
        return userQueryService.currentUser(jwt);
    }

    /**
     * Changes the password and immediately issues a new access token plus a new refresh cookie, so
     * the caller leaves the forced-change state without an extra round trip.
     */
    public AuthResult changePassword(long userId, ChangePasswordRequest request) {
        AuthenticatedUser user = passwordChangeService.change(userId, request.currentPassword(),
                request.newPassword());
        String refreshToken = refreshTokenService.issue(user.tenantId(), user.id());
        return new AuthResult(tokenService.issue(user), refreshToken);
    }

    public static long userId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new UnauthenticatedException();
        }
    }

    /**
     * Reuse is a security event: every session of the affected user is revoked. This runs in its
     * own transaction after the rotation transaction has finished, so the revokes are committed even
     * though the HTTP response is 401.
     */
    private void revokeAllAfterReuse(RefreshToken token) {
        refreshTokenService.revokeAllForUser(token.getUserId());
    }
}
