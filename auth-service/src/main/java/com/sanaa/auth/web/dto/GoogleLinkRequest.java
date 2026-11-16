package com.sanaa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirms ownership of an existing password account before linking Google. */
public record GoogleLinkRequest(@NotBlank String password) {
}
