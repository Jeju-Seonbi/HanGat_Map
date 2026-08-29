package com.example.hangat.common.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoServiceTest {

    private final GeoService geoService = new GeoService();

    @Test
    void 성산일출봉에서_혼인지까지_약_5킬로미터다() {
        // 실좌표 - 지도로 재면 약 5.3km. cos 보정 누락·lat/lng 순서 실수를 잡아주는 실세계 검증
        double distance = geoService.distanceKm(33.4581, 126.9425, 33.4335, 126.8930);

        assertThat(distance).isBetween(5.0, 5.7);
    }

    @Test
    void 같은_지점은_거리가_0이다() {
        assertThat(geoService.distanceKm(33.45, 126.94, 33.45, 126.94)).isZero();
    }

    @Test
    void 바운딩_박스는_반경_경계의_점을_포함한다() {
        GeoService.BoundingBox box = geoService.boxAround(33.45, 126.94, 10.0);

        double northLat = 33.45 + 10.0 / 111.32;
        assertThat(northLat).isLessThanOrEqualTo(box.maxLat());
        assertThat(box.minLng()).isLessThan(126.94);
        assertThat(box.maxLng()).isGreaterThan(126.94);
    }
}
