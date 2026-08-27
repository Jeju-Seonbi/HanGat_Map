package com.example.hangat.domain.place.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 장소 유형 - TourAPI contentTypeId(12/14/28/32/39) + 착한가격업소 업종에서 매핑 */
@AllArgsConstructor
@Getter
public enum PlaceCategory {
    TOURIST("관광지"),
    CULTURE("문화시설"),
    LEISURE("레포츠"),
    LODGING("숙박"),
    RESTAURANT("음식점"),
    CAFE("카페");

    private final String label;
}
