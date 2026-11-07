package com.sanaa.auth.web.dto;

import com.sanaa.auth.domain.TenantStatus;

public record TenantSummary(
        String id,
        String slug,
        String name,
        TenantStatus status,
        String primaryColor,
        String logoUrl) {
}
