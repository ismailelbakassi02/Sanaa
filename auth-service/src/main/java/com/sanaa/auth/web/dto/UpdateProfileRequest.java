package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Size;

/**
 * Own profile. {@code fullName} is optional: when present and changed, an INSTRUCTOR publishes an
 * {@code INSTRUCTOR_UPDATED} event so course snapshots can follow.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProfileRequest(
        @Size(min = 2, max = 120) String fullName,
        @Size(max = 120) String headline,
        @Size(max = 1000) String bio,
        @Size(max = 200) String websiteUrl,
        @Size(max = 200) String instagramUrl,
        @Size(max = 200) String linkedinUrl,
        @Size(max = 200) String youtubeUrl) {
}
