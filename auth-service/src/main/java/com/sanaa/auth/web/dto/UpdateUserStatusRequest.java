package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sanaa.auth.domain.UserStatus;

/**
 * Admin user moderation. The reason is mandatory: a suspension without a recorded motive is not
 * defensible in an oversight review.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserStatusRequest(
        @NotNull UserStatus status,
        @NotBlank @Size(min = 5, max = 300) String reason) {
}
