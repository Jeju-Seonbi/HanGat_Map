package com.example.hangat.course;

import java.util.Optional;

final class KakaoPlaceCategoryResolver {

    private KakaoPlaceCategoryResolver() {
    }

    static Optional<String> resolve(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        if (categoryName.contains("카페")) {
            return Optional.of("CAFE");
        }
        if (categoryName.contains("음식점") || categoryName.contains("식당")) {
            return Optional.of("FOOD");
        }
        if (categoryName.contains("숙박") || categoryName.contains("호텔")
                || categoryName.contains("리조트") || categoryName.contains("펜션")) {
            return Optional.of("LODGING");
        }
        if (categoryName.contains("여행") || categoryName.contains("관광")
                || categoryName.contains("명소")) {
            return Optional.of("TOURIST");
        }
        return Optional.empty();
    }
}
