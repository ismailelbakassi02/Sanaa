package com.sanaa.course.crosstenant;

import java.time.Instant;

public record ReviewRow(
        long id,
        String userName,
        int rating,
        String comment,
        String publisherReply,
        Instant repliedAt,
        Instant createdAt) {
}
