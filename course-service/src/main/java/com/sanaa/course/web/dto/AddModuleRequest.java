package com.sanaa.course.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The client never sends a position: the server assigns it as the current maximum plus one.
 */
public record AddModuleRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "must contain at most 120 characters")
        String title) {
}
