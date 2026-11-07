package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sanaa.common.security.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tenant admins create users in their own tenant only. There is deliberately no tenant field; a
 * tenant id in the body is ignored rather than trusted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTenantUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotNull Role role) {
}
