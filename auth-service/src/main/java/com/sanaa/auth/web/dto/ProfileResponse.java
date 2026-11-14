package com.sanaa.auth.web.dto;

public record ProfileResponse(
        long userId,
        String email,
        String fullName,
        String headline,
        String bio,
        String websiteUrl,
        String instagramUrl,
        String linkedinUrl,
        String youtubeUrl,
        String avatarImageId) {
}
