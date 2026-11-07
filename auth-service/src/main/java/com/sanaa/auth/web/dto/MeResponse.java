package com.sanaa.auth.web.dto;

public record MeResponse(
        long id,
        String email,
        String fullName,
        String role,
        String tenantId,
        String tenantSlug) {
}
