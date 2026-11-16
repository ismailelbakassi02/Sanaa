package com.sanaa.course.crosstenant;

import java.math.BigDecimal;

public record RatingAggregate(BigDecimal average, int count) {
}
