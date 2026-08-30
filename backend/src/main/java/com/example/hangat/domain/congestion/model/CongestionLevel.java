package com.example.hangat.domain.congestion.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 혼잡 등급 - 이름·순서를 프론트(utils/congestion.ts)와 동일하게 유지한다 */
@AllArgsConstructor
@Getter
public enum CongestionLevel {
    RELAXED("여유"),
    MODERATE("보통"),
    CROWDED("혼잡"),
    VERY_CROWDED("매우 혼잡");

    private final String label;
}
