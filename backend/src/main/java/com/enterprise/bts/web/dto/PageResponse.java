package com.enterprise.bts.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable, framework-independent pagination envelope (0-based page index). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
