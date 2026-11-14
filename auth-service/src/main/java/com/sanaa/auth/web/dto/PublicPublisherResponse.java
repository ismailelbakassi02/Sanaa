package com.sanaa.auth.web.dto;

import java.util.List;

/**
 * Public publisher page. No email, no contact email, no internal ids except the instructor ids that
 * the page needs to link to an instructor profile.
 */
public record PublicPublisherResponse(
        String slug,
        String name,
        String type,
        String headline,
        String description,
        String websiteUrl,
        String instagramUrl,
        String linkedinUrl,
        String youtubeUrl,
        String logoImageId,
        String primaryColor,
        List<PublicTeamMember> team) {

    public record PublicTeamMember(
            long userId,
            String fullName,
            String headline,
            String avatarImageId) {
    }
}
