package com.sanaa.auth.web.dto;

public record TeamMemberResponse(
        long userId,
        String email,
        String fullName,
        String role,
        String status,
        String headline,
        String avatarImageId,
        boolean mustChangePassword,
        java.time.Instant createdAt) {
}
