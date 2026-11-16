package com.sanaa.course.web.dto.studio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lesson metadata. The video itself is uploaded through a separate media endpoint; a lesson
 * without a ready video is shown as "Bientot disponible" on the public page.
 */
public record LessonRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String description,
        @Min(0) @Max(14400) int durationSeconds,
        boolean freePreview) {
}
