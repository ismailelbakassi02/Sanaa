package com.sanaa.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sanaa.auth.domain.TenantType;

/**
 * Publisher creation. A logged-in learner sends only the publisher fields; an anonymous caller also
 * sends the owner fields. The service rejects owner fields when a token is present instead of
 * silently ignoring them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatePublisherRequest(
        @NotNull TenantType publisherType,

        @NotBlank @Size(min = 2, max = 120) String publisherName,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$",
                message = "must be 3 to 63 lowercase letters, digits or hyphens and start and end "
                        + "with a letter or digit")
        String slug,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex color such as #195A56")
        String primaryColor,

        @Email @Size(max = 255) String contactEmail,

        @Size(min = 2, max = 120) String ownerFullName,

        @Email @Size(max = 255) String ownerEmail,

        @Size(max = 200) String password) {
}
