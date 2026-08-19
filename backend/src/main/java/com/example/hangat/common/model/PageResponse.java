package com.example.hangat.common.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/** 페이징 응답 포맷 (Nexus 컨벤션) - 프론트 저장 코스 목록 등 page/size 계약과 대응 */
@Getter
@Builder
public class PageResponse<T> {
    private final List<T> content;
    private final int number;
    private final int size;
    private final int totalPages;
    private final long totalElements;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .number(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }
}
