package com.sanaa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** First local password for an account originally created with Google. */
public record SetPasswordRequest(@NotBlank String newPassword) {
}
