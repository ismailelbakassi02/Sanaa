package com.sanaa.course.crosstenant;

import java.time.Instant;

/**
 * The caller's own review. Deliberately status-agnostic: a HIDDEN review is still visible to the
 * learner who wrote it, with its status, because the page must explain the moderation decision.
 */
public record MyReviewRow(
        long id,
        long courseId,
        int rating,
        String comment,
        String status,
        String publisherReply,
        Instant repliedAt,
        Instant createdAt,
        Instant updatedAt) {
}
