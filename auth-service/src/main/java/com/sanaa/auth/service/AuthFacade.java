package com.sanaa.auth.service;

import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.error.InvalidCredentialsException;
import com.sanaa.auth.web.dto.BrandingResponse;
import com.sanaa.auth.web.dto.LoginRequest;
import com.sanaa.auth.web.dto.LoginResponse;
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

    public AuthFacade(TenantLookupService tenantLookupService,
                      RegistrationService registrationService,
                      LoginService loginService,
                      PasswordVerifier passwordVerifier,
                      TokenService tokenService,
                      UserQueryService userQueryService) {
        this.tenantLookupService = tenantLookupService;
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.passwordVerifier = passwordVerifier;
        this.tokenService = tokenService;
        this.userQueryService = userQueryService;
    }

    public RegisterResponse register(RegisterRequest request) {
        Tenant tenant = tenantLookupService.requireActiveBySlug(request.tenantSlug());
        AuthenticatedUser user = TenantContext.callAs(tenant.getId(),
                () -> registrationService.register(tenant, request));
        return new RegisterResponse(user.id(), user.email(), user.fullName(), user.tenantSlug(),
                user.role().name());
    }

    public LoginResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        Optional<Tenant> tenant = tenantLookupService.findBySlug(request.tenantSlug());

        // Unknown and suspended tenants follow the same dummy BCrypt path as an unknown email.
        if (tenant.isEmpty() || tenant.get().getStatus() != TenantStatus.ACTIVE) {
            passwordVerifier.performDummyVerification(request.password());
            throw new InvalidCredentialsException();
        }

        AuthenticatedUser user = TenantContext.callAs(tenant.get().getId(),
                () -> loginService.login(tenant.get(), email, request.password()));
        return tokenService.issue(user);
    }

    public MeResponse me(Jwt jwt) {
        // JwtTenantFilter already set the tenant before this call. Do not start a transaction here.
        return userQueryService.currentUser(jwt);
    }

    public BrandingResponse branding(String slug) {
        Tenant tenant = tenantLookupService.requireActiveBySlug(slug);
        return new BrandingResponse(tenant.getName(), tenant.getPrimaryColor(),
                tenant.getLogoUrl());
    }
}
