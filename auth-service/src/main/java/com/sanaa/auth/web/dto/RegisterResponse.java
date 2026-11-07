package com.sanaa.auth.web.dto;

public record RegisterResponse(
        long id,
        String email,
        String fullName,
        String tenantSlug,
        String role) {
}
