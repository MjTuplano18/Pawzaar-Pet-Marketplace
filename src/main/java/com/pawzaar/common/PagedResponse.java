package com.pawzaar.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable, framework-agnostic envelope for paginated list responses.
 *
 * <p>Returning Spring's {@code Page<T>} directly couples the API JSON shape to the framework.
 * If Spring ever changes what {@code Page} serializes, every client breaks. This record pins
 * the contract: content, page, size, totalElements, totalPages - nothing else leaks out.
 *
 * <p>Usage in a service:
 * <pre>
 *   return PagedResponse.of(repository.findAll(pageable));
 * </pre>
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** Convenience factory - converts a Spring {@code Page} into this record. */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
