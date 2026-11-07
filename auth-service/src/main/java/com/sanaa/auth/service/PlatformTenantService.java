package com.sanaa.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.EmailAlreadyUsedException;
import com.sanaa.auth.error.SlugAlreadyUsedException;
import com.sanaa.auth.error.TenantNotFoundException;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.web.dto.CreateTenantRequest;
import com.sanaa.auth.web.dto.PageResponse;
import com.sanaa.auth.web.dto.TenantSummary;
import com.sanaa.common.security.Role;

/**
 * Platform tenant administration.
 *
 * <p>{@link #createTenant} is called inside {@code TenantContext.callAs(newTenantId)}, so the first
 * admin insert receives the new tenant id from {@code @TenantId} and both rows commit in one
 * transaction.
 */
@Service
public class PlatformTenantService {

    static final String PLATFORM_TENANT_SLUG = TenantSlugs.PLATFORM;
    private static final String DEFAULT_PRIMARY_COLOR = "#165DFF";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public PlatformTenantService(TenantRepository tenantRepository,
                                 UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TenantSummary createTenant(String tenantId, String slug, CreateTenantRequest request) {
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new SlugAlreadyUsedException();
        }

        Tenant tenant = new Tenant(tenantId, slug, request.name().trim(), TenantStatus.ACTIVE,
                DEFAULT_PRIMARY_COLOR, null);
        try {
            tenantRepository.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException exception) {
            throw new SlugAlreadyUsedException();
        }

        String adminEmail = EmailNormalizer.normalize(request.adminEmail());
        User admin = new User(adminEmail, passwordEncoder.encode(request.adminPassword()),
                request.adminFullName().trim(), Role.TENANT_ADMIN);
        try {
            userRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyUsedException();
        }

        return summary(tenant);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantSummary> list(int page, int size) {
        Page<Tenant> result = tenantRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(
                result.getContent().stream().map(PlatformTenantService::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public TenantSummary updateStatus(String tenantId, TenantStatus status) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(TenantNotFoundException::new);

        if (status == TenantStatus.SUSPENDED
                && PLATFORM_TENANT_SLUG.equals(tenant.getSlug())) {
            throw new BadRequestException("The platform tenant cannot be suspended.");
        }

        if (status == TenantStatus.SUSPENDED) {
            tenant.suspend();
            // Suspension revokes every refresh session of the tenant. Existing access tokens stay
            // valid until they expire (15 minutes), which is documented in ADR 003.
            refreshTokenService.revokeAllForTenant(tenant.getId());
        } else {
            tenant.activate();
        }
        tenantRepository.saveAndFlush(tenant);
        return summary(tenant);
    }

    private static TenantSummary summary(Tenant tenant) {
        return new TenantSummary(tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getStatus(), tenant.getPrimaryColor(), tenant.getLogoUrl());
    }
}
