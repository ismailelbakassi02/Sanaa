package com.sanaa.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,63}$",
                message = "must contain only lowercase letters, digits and hyphens, 3 to 63 characters")
        String tenantSlug,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 10, max = 72)
        String password,

        @NotBlank
        @Size(min = 2, max = 120)
        String fullName) {
}
