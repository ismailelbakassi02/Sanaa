package com.sanaa.course.web.dto.learner;

import java.time.Instant;

import com.sanaa.course.domain.Review;

/**
 * My own review, including the publisher reply if there is one.
 *
 * <p>{@code status} is VISIBLE or HIDDEN: a learner always sees their own review, even after
 * moderation hid it, so the form can explain why it no longer appears publicly.
 */
public record MyReviewResponse(
        long id,
        long courseId,
        int rating,
        String comment,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String publisherReply,
        Instant repliedAt) {

    public static MyReviewResponse from(Review review) {
        return new MyReviewResponse(
                review.getId(),
                review.getCourseId(),
                review.getRating(),
                review.getComment(),
                review.getStatus().name(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getPublisherReply(),
                review.getRepliedAt());
    }
}
