package com.sanaa.course.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The client never sends a position: the server assigns it as the current maximum plus one.
 */
public record AddLessonRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "must contain at most 120 characters")
        String title,

        @NotNull(message = "must not be null")
        @Min(value = 0, message = "must be zero or greater")
        Integer durationSeconds,

        Boolean freePreview) {
}
