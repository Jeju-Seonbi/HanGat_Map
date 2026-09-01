package com.example.hangat.course.service;

import com.example.hangat.common.geo.GeoService;
import com.example.hangat.map.model.entity.Place;
import org.springframework.stereotype.Component;

/**
 * 코스 슬롯 간 이동 거리·시간 계산 - 샘플 배치와 스왑이 공유한다.
 *
 * <p><b>추정치임을 숨기지 않는다</b>: 직선거리(하버사인)를 평균 속도로 나눈 값이라
 * 실제 도로 경로가 아니다. 화면 문구도 "약 N분"까지만 말한다.
 * 정확한 경로가 필요해지면 여기만 지도 API 호출로 바꾸면 된다.
 */
@Component
public class CourseTravelCalculator {

    /** 렌터카 기준 평균 속도(km/h) - 제주 일반도로 체감치. */
    private static final double AVG_SPEED_KMH = 40.0;

    private final GeoService geoService;

    public CourseTravelCalculator(GeoService geoService) {
        this.geoService = geoService;
    }

    /** 직전 장소에서 이 장소까지. 첫 슬롯(previous == null)은 값이 없다 - 0으로 채우지 않는다. */
    public Travel between(Place previous, Place current) {
        if (previous == null || previous.getLatitude() == null || current.getLatitude() == null) {
            return Travel.NONE;
        }
        double km = geoService.distanceKm(
                previous.getLatitude(), previous.getLongitude(),
                current.getLatitude(), current.getLongitude());
        return new Travel(
                (int) Math.round(km * 1000),
                (short) Math.max(1, Math.ceil(km / AVG_SPEED_KMH * 60)));
    }

    /** 이동 거리(m)·소요 시간(분). 값이 없으면 두 필드 모두 null. */
    public record Travel(Integer distanceM, Short minutes) {
        static final Travel NONE = new Travel(null, null);
    }
}
