package com.islamshariful.authservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A stable pagination envelope.
 *
 * <p>Serialising Spring Data's {@code Page} straight to JSON is a common shortcut and a genuine API defect:
 * the shape is an implementation detail (Boot 3.3 started warning about it), it leaks {@code Pageable} and
 * {@code Sort} internals, and it changes between Spring versions. This is our contract instead.
 */
@Schema(description = "One page of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "137") long totalElements,
        @Schema(example = "7") int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
