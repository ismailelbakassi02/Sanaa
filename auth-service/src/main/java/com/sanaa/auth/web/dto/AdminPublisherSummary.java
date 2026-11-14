package com.sanaa.auth.web.dto;

import java.time.Instant;

/**
 * Admin publisher row, with the number of active members as a light operational signal.
 */
public record AdminPublisherSummary(
        String id,
        String slug,
        String name,
        String type,
        String status,
        String primaryColor,
        String contactEmail,
        String headline,
        long activeMembers,
        Instant createdAt) {
}
