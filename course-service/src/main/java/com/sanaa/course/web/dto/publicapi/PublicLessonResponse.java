package com.sanaa.course.web.dto.publicapi;

/**
 * A curriculum line. Only the fact that a ready video exists is public; the media path, the HLS
 * key and the job state are never exposed, so the player cannot be bypassed.
 */
public record PublicLessonResponse(
        long id,
        String title,
        String description,
        int position,
        int durationSeconds,
        boolean freePreview,
        boolean available) {
}
