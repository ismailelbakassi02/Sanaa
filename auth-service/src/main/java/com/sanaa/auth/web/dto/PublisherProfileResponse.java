package com.sanaa.auth.web.dto;

import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.TenantType;

public record PublisherProfileResponse(
        String slug,
        String name,
        TenantType type,
        TenantStatus status,
        String headline,
        String description,
        String websiteUrl,
        String instagramUrl,
        String linkedinUrl,
        String youtubeUrl,
        String logoImageId,
        String primaryColor,
        String contactEmail) {
}
