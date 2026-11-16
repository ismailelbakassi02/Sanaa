package com.sanaa.course.web.dto.learner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** A learner review. The comment is plain text; the client must render it as text, not HTML. */
public record ReviewRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String comment) {
}
