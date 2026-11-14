package com.sanaa.auth.web.dto;

import com.sanaa.auth.domain.TenantType;

/**
 * The created publisher. No token and no cookie: a member receives publisher claims on the next
 * refresh, and an anonymous owner logs in explicitly.
 */
public record CreatedPublisherResponse(
        String slug,
        String name,
        TenantType type) {
}
