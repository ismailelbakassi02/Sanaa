package com.sanaa.course.web.dto.studio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * A discount is a price strictly below the list price plus an end date. The service rejects a
 * discount that is not below the current price, so the database CHECK is a second line of defence.
 */
public record DiscountRequest(
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Future Instant endsAt) {
}
