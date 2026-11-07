package com.sanaa.auth.web.dto;

import com.sanaa.common.security.Role;

public record CreatedUserResponse(
        long id,
        String email,
        String fullName,
        Role role,
        String tenantId) {
}
