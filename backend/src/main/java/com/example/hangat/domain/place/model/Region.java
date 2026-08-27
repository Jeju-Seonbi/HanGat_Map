package com.example.hangat.domain.place.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 제주 5권역 - 팀 자체 매핑 (집중률 API는 시군구 2개만 제공하므로 주소 기반으로 나눈다)
 * 값 고정 컬럼은 ENUM(STRING 저장)이 팀 컨벤션
 */
@AllArgsConstructor
@Getter
public enum Region {
    JEJU_CITY("제주시내"),
    SEOGWIPO("서귀포시내"),
    EAST("동부"),
    WEST("서부"),
    SOUTH("남부");

    private final String label;
}
