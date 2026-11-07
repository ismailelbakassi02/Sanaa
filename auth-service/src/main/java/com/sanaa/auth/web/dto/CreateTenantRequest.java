package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTenantRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,63}$",
                message = "must contain only lowercase letters, digits and hyphens, 3 to 63 characters")
        String slug,

        @NotBlank @Size(max = 120) String name,

        @NotBlank @Email @Size(max = 255) String adminEmail,

        @NotBlank @Size(min = 10, max = 72) String adminPassword,

        @NotBlank @Size(min = 2, max = 120) String adminFullName) {
}
