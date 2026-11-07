package com.sanaa.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AdminUserService;
import com.sanaa.auth.web.dto.CreateTenantUserRequest;
import com.sanaa.auth.web.dto.CreatedUserResponse;

/**
 * Tenant administration. The class-level role check intentionally excludes SUPER_ADMIN: platform
 * staff cannot manage tenant content.
 */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public CreatedUserResponse createUser(@Valid @RequestBody CreateTenantUserRequest request) {
        return adminUserService.createUser(request);
    }
}
