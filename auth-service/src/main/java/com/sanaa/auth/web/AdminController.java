package com.sanaa.auth.web;

import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.service.AdminService;
import com.sanaa.auth.web.dto.AdminAuditEntry;
import com.sanaa.auth.web.dto.AdminPublisherSummary;
import com.sanaa.auth.web.dto.AdminUserSummary;
import com.sanaa.auth.web.dto.PageResponse;
import com.sanaa.auth.web.dto.UpdatePublisherStatusRequest;
import com.sanaa.auth.web.dto.UpdateUserStatusRequest;

/**
 * Platform oversight. Only SUPER_ADMIN reaches this controller; every call is audited.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public PageResponse<AdminUserSummary> users(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listUsers(q, page, size, jwt);
    }

    @GetMapping("/users/{id}")
    public AdminUserSummary user(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return adminService.userDetail(id, jwt);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserSummary updateUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminService.updateUserStatus(id, request.status(), request.reason(), jwt);
    }

    @GetMapping("/publishers")
    public PageResponse<AdminPublisherSummary> publishers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listPublishers(q, page, size, jwt);
    }

    @PatchMapping("/publishers/{id}/status")
    public AdminPublisherSummary updatePublisherStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdatePublisherStatusRequest request) {
        return adminService.updatePublisherStatus(id, request.status(), request.reason(), jwt);
    }

    @GetMapping("/audit/auth")
    public PageResponse<AdminAuditEntry> audit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listAudit(actor, action, from, to, page, size, jwt);
    }
}
