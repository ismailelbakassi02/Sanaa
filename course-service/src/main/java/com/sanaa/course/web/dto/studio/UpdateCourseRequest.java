package com.sanaa.course.web.dto.studio;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full catalogue update of a course. {@code version} is the value the client last read; a mismatch
 * is a 409 so two editors cannot silently overwrite each other.
 */
public record UpdateCourseRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 200) String summary,
        @Size(max = 4000) String description,
        Long categoryId,
        String level,
        @Pattern(regexp = "fr|ar|en", message = "must be one of fr, ar, en") String language,
        @Size(max = 12) List<@NotBlank @Size(max = 200) String> skills,
        @Size(max = 12) List<@NotBlank @Size(max = 200) String> requirements,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(max = 3) String currency,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags) {
}
