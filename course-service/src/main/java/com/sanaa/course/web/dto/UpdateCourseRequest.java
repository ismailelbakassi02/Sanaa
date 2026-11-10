package com.sanaa.course.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @NotBlank(message = "must not be blank")
        @Size(min = 3, max = 120, message = "must contain between 3 and 120 characters")
        String title,

        @Size(max = 4000, message = "must contain at most 4000 characters")
        String description,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.00", message = "must be at least 0.00")
        @DecimalMax(value = "1000000.00", message = "must be at most 1000000.00")
        @Digits(integer = 7, fraction = 2, message = "must have at most 2 decimals")
        BigDecimal price,

        @Pattern(regexp = "MAD", message = "must be MAD")
        String currency,

        @NotNull(message = "must not be null")
        @PositiveOrZero(message = "must not be negative")
        Long version) {
}
