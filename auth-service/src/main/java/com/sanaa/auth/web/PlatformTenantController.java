package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.PlatformFacade;
import com.sanaa.auth.web.dto.CreateTenantRequest;
import com.sanaa.auth.web.dto.PageResponse;
import com.sanaa.auth.web.dto.TenantSummary;
import com.sanaa.auth.web.dto.UpdateTenantStatusRequest;

/**
 * Platform administration is limited to SUPER_ADMIN. A tenant admin or student must get 403 before
 * any handler runs.
 */
@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformTenantController {

    private final PlatformFacade platformFacade;

    public PlatformTenantController(PlatformFacade platformFacade) {
        this.platformFacade = platformFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantSummary createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return platformFacade.createTenant(request);
    }

    @GetMapping
    public PageResponse<TenantSummary> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return platformFacade.listTenants(page, size);
    }

    @PatchMapping("/{id}/status")
    public TenantSummary updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateTenantStatusRequest request) {
        return platformFacade.updateStatus(id, request.status());
    }
}
