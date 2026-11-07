package com.sanaa.auth.web.dto;

import com.sanaa.auth.domain.TenantStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(@NotNull TenantStatus status) {
}
