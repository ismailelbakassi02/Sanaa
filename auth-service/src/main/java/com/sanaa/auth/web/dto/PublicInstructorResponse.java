package com.sanaa.auth.web.dto;

/**
 * Public instructor page. No email, no membership id, no tenant id: only the publisher slug and
 * name needed for the page header.
 */
public record PublicInstructorResponse(
        long userId,
        String fullName,
        String headline,
        String bio,
        String websiteUrl,
        String instagramUrl,
        String linkedinUrl,
        String youtubeUrl,
        String avatarImageId,
        String publisherSlug,
        String publisherName) {
}
