package com.example.hangat.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoPlaceCategoryResolverTest {

    @Test
    void mapsOnlySupportedKakaoCategoryFacts() {
        assertThat(KakaoPlaceCategoryResolver.resolve("여행 > 관광,명소 > 자연명소"))
                .contains("TOURIST");
        assertThat(KakaoPlaceCategoryResolver.resolve("음식점 > 카페"))
                .contains("CAFE");
        assertThat(KakaoPlaceCategoryResolver.resolve("음식점 > 한식"))
                .contains("FOOD");
        assertThat(KakaoPlaceCategoryResolver.resolve("숙박 > 호텔"))
                .contains("LODGING");
        assertThat(KakaoPlaceCategoryResolver.resolve("병원 > 내과")).isEmpty();
        assertThat(KakaoPlaceCategoryResolver.resolve(null)).isEmpty();
    }
}
