package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sanaa.auth.domain.TenantStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePublisherStatusRequest(
        @NotNull TenantStatus status,
        @NotBlank @Size(min = 5, max = 300) String reason) {
}
