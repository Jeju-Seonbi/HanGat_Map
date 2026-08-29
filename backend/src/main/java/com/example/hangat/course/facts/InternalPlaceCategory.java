package com.example.hangat.course.facts;

public record InternalPlaceCategory(
        Long categoryId,
        String code,
        String name
) {
    public InternalPlaceCategory {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("내부 장소 카테고리 코드는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("내부 장소 카테고리명은 필수입니다.");
        }
    }
}
