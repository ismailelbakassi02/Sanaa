package com.sanaa.course.crosstenant;

import java.math.BigDecimal;
import java.util.List;

/** Validated public search input. The service normalizes and caps it before it reaches SQL. */
public record PublicCourseSearch(
        String q,
        String categorySlug,
        String tag,
        String level,
        boolean freeOnly,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String sort,
        int page,
        int size) {
}
