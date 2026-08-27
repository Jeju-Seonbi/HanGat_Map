package com.example.hangat.domain.main.model;

import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.map.model.entity.Place;

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

    /**
     * @param place map 도메인의 장소 엔티티. 권역·카테고리가 enum이 아니라 마스터 테이블이라
     *              표시명은 {@code getName()}으로 꺼낸다
     */
    public static CalmPlaceResponse of(Place place, double rate, CongestionLevel level, String reason) {
        return new CalmPlaceResponse(
                place.getId(),
                place.getName(),
                place.getRegion().getName(),
                place.getPrimaryCategory().getName(),
                // 사진은 place_images 테이블로 따로 적재한다(MAP-08, 미착수) - 그때까지 null.
                // 빈 문자열로 두면 프론트가 깨진 이미지를 그린다
                null,
                rate,
                level,
                level.getLabel(),
                reason
        );
    }
}
