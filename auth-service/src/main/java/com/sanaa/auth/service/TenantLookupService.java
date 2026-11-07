package com.sanaa.auth.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.error.TenantNotFoundException;
import com.sanaa.auth.repository.TenantRepository;

/**
 * The tenants table is global, so this lookup is intentionally outside any tenant context.
 */
@Service
public class TenantLookupService {

    private final TenantRepository tenantRepository;

    public TenantLookupService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findBySlug(String slug) {
        return tenantRepository.findBySlug(normalizeSlug(slug));
    }

    @Transactional(readOnly = true)
    public Tenant requireActiveBySlug(String slug) {
        return tenantRepository.findBySlug(normalizeSlug(slug))
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(TenantNotFoundException::new);
    }

    /**
     * Public registration is closed for reserved labels. The check runs before the lookup so a
     * reserved slug returns the same 404 as an unknown tenant and never reveals whether the label
     * exists.
     */
    @Transactional(readOnly = true)
    public Tenant requireActiveForRegistration(String slug) {
        String normalized = normalizeSlug(slug);
        if (normalized == null || TenantSlugs.isReserved(normalized)) {
            throw new TenantNotFoundException();
        }
        return tenantRepository.findBySlug(normalized)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(TenantNotFoundException::new);
    }

    public static String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }
}
