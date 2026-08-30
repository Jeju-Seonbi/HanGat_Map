package com.example.hangat.domain.alternative.model;

import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.map.model.entity.Place;

/** 과밀 스팟의 대안 장소 한 곳 (#과밀지역우회) - 스왑 시트의 카드 한 장 */
public record AlternativePlaceResponse(
        Long placeId,
        String name,
        String regionLabel,
        String categoryLabel,
        String imageUrl,
        double rate,
        CongestionLevel level,
        String levelLabel,
        double distanceKm,
        String reason
) {
    public static AlternativePlaceResponse of(Place place, double rate, CongestionLevel level,
                                              double distanceKm, String reason) {
        return new AlternativePlaceResponse(
                place.getId(),
                place.getName(),
                place.getRegion().getName(),
                place.getPrimaryCategory().getName(),
                place.getImageUrl(),
                rate,
                level,
                level.getLabel(),
                Math.round(distanceKm * 10) / 10.0,   // 5.34 → 5.3 (화면 표기용)
                reason
        );
    }
}
