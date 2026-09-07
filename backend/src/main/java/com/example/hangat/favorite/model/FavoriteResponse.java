package com.example.hangat.favorite.model;

import com.example.hangat.map.model.entity.Place;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 마이페이지 찜 카드 응답.
 * 장소의 실제 정보를 사용하고, 수집되지 않은 정보는 null로 유지한다.
 */

public record FavoriteResponse(
        Long placeId,
        String name,
        String category,
        String region,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        BigDecimal rating,
        int reviewCount,
        String businessStatus,
        String operatingHours,
        String feeText,
        LocalDateTime createdAt
) {
    public static FavoriteResponse from(Favorite favorite) {
        Place place = favorite.getPlace();

        String address = place.getRoadAddress();
        if (address == null || address.isBlank()) {
            address = place.getLotAddress();
        }

        // 별점 없는 혼잡 제보만 있을 수도 있으므로 0점을 실제 평점으로 표시하지 않는다.
        BigDecimal rating = place.getRatingAvg();
        if (rating != null && rating.signum() == 0) {
            rating = null;
        }

        return new FavoriteResponse(
                place.getId(),
                place.getName(),
                place.getPrimaryCategory().getName(),
                place.getRegion().getName(),
                address,
                place.getLatitude(),
                place.getLongitude(),
                place.getImageUrl(),
                rating,
                place.getReviewCount(),
                place.getBusinessStatus().name(),
                place.getOperatingHoursText(),
                place.getUseFeeText(),
                favorite.getCreatedAt()
        );
    }
}
