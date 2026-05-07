package com.assessment.employee.dto.response;

import java.util.List;

/**
 * Immutable paginated response wrapper.
 *
 * <p>{@code first} and {@code last} are derived from {@code page} and
 * {@code totalPages}; use the 5-argument factory method
 * {@link #of(List, int, int, long, int)} so callers never compute them manually.
 *
 * @param <T> item type
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /**
     * Preferred factory — computes {@code first} and {@code last} automatically.
     */
    public static <T> PagedResponse<T> of(List<T> content,
                                           int page,
                                           int size,
                                           long totalElements,
                                           int totalPages) {
        return new PagedResponse<>(
                content, page, size, totalElements, totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }
}
