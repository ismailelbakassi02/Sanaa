package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Publisher profile update. All fields are required to keep the form a full replacement, which
 * avoids "was null a clear or a missing field?" ambiguity on the wire.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePublisherRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 160) String headline,
        @Size(max = 2000) String description,
        @Size(max = 200) String websiteUrl,
        @Size(max = 200) String instagramUrl,
        @Size(max = 200) String linkedinUrl,
        @Size(max = 200) String youtubeUrl,
        @NotBlank
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex color such as #195A56")
        String primaryColor,
        @Email @Size(max = 255) String contactEmail) {
}
