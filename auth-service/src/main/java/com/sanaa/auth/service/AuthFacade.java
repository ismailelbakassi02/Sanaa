package com.sanaa.auth.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sanaa.auth.domain.RefreshToken;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.error.UnauthenticatedException;
import com.sanaa.auth.web.dto.BrandingResponse;
import com.sanaa.auth.web.dto.LoginRequest;
import com.sanaa.auth.web.dto.MeResponse;
import com.sanaa.auth.web.dto.RegisterRequest;
import com.sanaa.auth.web.dto.RegisterResponse;
import com.sanaa.common.tenant.TenantContext;

/**
 * Entry point for unauthenticated tenant-aware flows.
 *
 * <p>WHY this bean is deliberately NOT {@code @Transactional}: the tenant slug must be resolved
 * first, then {@link TenantContext} must be set, and only then may a transaction open. If this
 * method opened the transaction, Hibernate would resolve its tenant before the context exists and
 * would silently run under the fail-closed nil tenant. Resolving the tenant uses
 * {@link TenantLookupService} (tenants are global), and the tenant-owned work happens in the
 * separate transactional service inside {@link TenantContext#callAs}.
 */
@Service
public class AuthFacade {

    private final TenantLookupService tenantLookupService;
    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final PasswordVerifier passwordVerifier;
    private final TokenService tokenService;
    private final UserQueryService userQueryService;
    private final RefreshTokenLookupService refreshTokenLookupService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshRotationService refreshRotationService;
    private final RefreshTokenCodec refreshTokenCodec;
    private final Clock clock;

    public AuthFacade(TenantLookupService tenantLookupService,
                      RegistrationService registrationService,
                      LoginService loginService,
                      PasswordVerifier passwordVerifier,
                      TokenService tokenService,
                      UserQueryService userQueryService,
                      RefreshTokenLookupService refreshTokenLookupService,
                      RefreshTokenService refreshTokenService,
                      RefreshRotationService refreshRotationService,
                      RefreshTokenCodec refreshTokenCodec,
                      Clock clock) {
        this.tenantLookupService = tenantLookupService;
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.passwordVerifier = passwordVerifier;
        this.tokenService = tokenService;
        this.userQueryService = userQueryService;
        this.refreshTokenLookupService = refreshTokenLookupService;
        this.refreshTokenService = refreshTokenService;
        this.refreshRotationService = refreshRotationService;
        this.refreshTokenCodec = refreshTokenCodec;
        this.clock = clock;
    }

    public RegisterResponse register(RegisterRequest request) {
        // Reserved slugs are rejected here, before any lookup, so they look exactly like an unknown
        // tenant. In particular, a public registration can never join the platform tenant.
        Tenant tenant = tenantLookupService.requireActiveForRegistration(request.tenantSlug());
        AuthenticatedUser user = TenantContext.callAs(tenant.getId(),
                () -> registrationService.register(tenant, request));
        return new RegisterResponse(user.id(), user.email(), user.fullName(), user.tenantSlug(),
                user.role().name());
    }

    public AuthResult login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        Optional<Tenant> tenant = tenantLookupService.findBySlug(request.tenantSlug());

        // Unknown and suspended tenants follow the same dummy BCrypt path as an unknown email.
        if (tenant.isEmpty() || tenant.get().getStatus() != TenantStatus.ACTIVE) {
            passwordVerifier.performDummyVerification(request.password());
            throw new InvalidCredentialsException();
        }

        LoginSession session = TenantContext.callAs(tenant.get().getId(), () -> {
            AuthenticatedUser user = loginService.login(tenant.get(), email, request.password());
            String refreshToken = refreshTokenService.issue(tenant.get().getId(), user.id());
            return new LoginSession(user, refreshToken);
        });
        return new AuthResult(tokenService.issue(session.user()), session.refreshToken());
    }

    /**
     * Refreshes a session. The lookup is global because only the cookie is available; the tenant is
     * read from the row and set before the rotation transaction starts.
     */
    public AuthResult refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new UnauthenticatedException();
        }

        String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
        Optional<RefreshToken> found = refreshTokenLookupService.findByHash(tokenHash);
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

        RefreshRotation rotation = TenantContext.callAs(presented.getTenantId(),
                () -> refreshRotationService.rotate(presented));

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
                .ifPresent(token -> TenantContext.runAs(token.getTenantId(),
                        () -> refreshTokenService.revokeById(token.getId())));
    }

    public MeResponse me(Jwt jwt) {
        // JwtTenantFilter already set the tenant before this call. Do not start a transaction here.
        return userQueryService.currentUser(jwt);
    }

    public BrandingResponse branding(String slug) {
        Tenant tenant = tenantLookupService.requireActiveBySlug(slug);
        // Public registration is impossible for the platform tenant (reserved slug), so the SPA
        // must not offer the account-creation link there either.
        boolean registrationEnabled = !TenantSlugs.PLATFORM.equals(tenant.getSlug());
        return new BrandingResponse(tenant.getName(), tenant.getPrimaryColor(),
                tenant.getLogoUrl(), registrationEnabled);
    }

    /**
     * Reuse is a security event: every session of the affected user is revoked. This runs in its
     * own transaction after the rotation transaction has finished, so the revokes are committed even
     * though the HTTP response is 401.
     */
    private void revokeAllAfterReuse(RefreshToken token) {
        TenantContext.runAs(token.getTenantId(),
                () -> refreshTokenService.revokeAllForUser(token.getUserId()));
    }

    private record LoginSession(AuthenticatedUser user, String refreshToken) {
    }
}
