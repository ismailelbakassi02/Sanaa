package com.sanaa.course.web.dto.publicapi;

import java.time.Instant;

/** A visible review. {@code learnerName} is masked ("Prenom L.") by the read model. */
public record PublicReviewResponse(
        long id,
        String learnerName,
        int rating,
        String comment,
        String publisherReply,
        Instant repliedAt,
        Instant createdAt) {
}
