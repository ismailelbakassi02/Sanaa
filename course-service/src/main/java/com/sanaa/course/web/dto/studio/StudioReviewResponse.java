package com.sanaa.course.web.dto.studio;

import java.time.Instant;

import com.sanaa.course.domain.Review;

public record StudioReviewResponse(
        long id,
        long courseId,
        String learnerName,
        int rating,
        String comment,
        String status,
        Instant createdAt,
        String publisherReply,
        Instant repliedAt) {

    public static StudioReviewResponse from(Review review) {
        return new StudioReviewResponse(
                review.getId(),
                review.getCourseId(),
                review.getUserName(),
                review.getRating(),
                review.getComment(),
                review.getStatus().name(),
                review.getCreatedAt(),
                review.getPublisherReply(),
                review.getRepliedAt());
    }
}
