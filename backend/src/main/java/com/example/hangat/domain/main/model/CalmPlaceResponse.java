package com.example.hangat.domain.main.model;

import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.domain.place.model.Place;

/** 메인 "오늘 한적한 곳" 카드 한 장 (MAIN_001) */
public record CalmPlaceResponse(
        Long placeId,
        String name,
        String regionLabel,
        String categoryLabel,
        String imageUrl,
        double rate,
        CongestionLevel level,
        String levelLabel,
        String reason
) {

    public static CalmPlaceResponse of(Place place, double rate, CongestionLevel level, String reason) {
        return new CalmPlaceResponse(
                place.getId(),
                place.getName(),
                place.getRegion().getLabel(),
                place.getCategory().getLabel(),
                place.getImageUrl(),
                rate,
                level,
                level.getLabel(),
                reason
        );
    }
}
