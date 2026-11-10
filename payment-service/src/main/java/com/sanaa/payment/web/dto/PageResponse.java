package com.sanaa.payment.web.dto;

import java.util.List;

/**
 * Paginated envelope shared with the other services.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
