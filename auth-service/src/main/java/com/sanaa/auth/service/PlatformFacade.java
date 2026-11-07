package com.sanaa.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.SlugAlreadyUsedException;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.web.dto.CreateTenantRequest;
import com.sanaa.auth.web.dto.PageResponse;
import com.sanaa.auth.web.dto.TenantSummary;
import com.sanaa.common.tenant.TenantContext;

/**
 * Entry point for platform administration.
 *
 * <p>Tenant creation is deliberately split: this non-transactional facade validates the slug and
 * generates the new id, then sets the tenant context, and only then calls the transactional service.
 * The new admin row therefore receives the correct discriminator, and the tenant plus its first
 * admin commit atomically.
 */
@Service
public class PlatformFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantLookupService tenantLookupService;
    private final PlatformTenantService platformTenantService;

    public PlatformFacade(TenantLookupService tenantLookupService,
                          PlatformTenantService platformTenantService) {
        this.tenantLookupService = tenantLookupService;
        this.platformTenantService = platformTenantService;
    }

    public TenantSummary createTenant(CreateTenantRequest request) {
        String slug = TenantSlugs.normalize(request.slug());
        if (!TenantSlugs.isValid(slug)) {
            throw new BadRequestException(
                    "Slug must contain only lowercase letters, digits and hyphens, 3 to 63 characters.");
        }
        if (TenantSlugs.isReserved(slug)) {
            throw new BadRequestException("Tenant slug is reserved.");
        }
        if (tenantLookupService.findBySlug(slug).isPresent()) {
            throw new SlugAlreadyUsedException();
        }

        String tenantId = UUID.randomUUID().toString();
        return TenantContext.callAs(tenantId,
                () -> platformTenantService.createTenant(tenantId, slug, request));
    }

    public PageResponse<TenantSummary> listTenants(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "page must be zero or greater and size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return platformTenantService.list(page, size);
    }

    public TenantSummary updateStatus(String tenantId, TenantStatus status) {
        return platformTenantService.updateStatus(tenantId, status);
    }
}
