package com.example.hangat.map.model.dto;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.enums.BusinessStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 장소 상세 한 건 ({@code GET /api/places/{id}}) - 관광지 상세 화면(§1.1, §2.1)
 *
 * <p>목록 필드 + 소개글·휴무일·평점 요약. 사진(place_images)은 엔티티가 아직 없어 빠져 있고,
 * 생기면 {@code List<PlaceImageResponse>} 필드 + {@link #from(Place)} 한 줄로 붙는다.
 *
 * <p>변환은 팀 공통 {@code PageResponse.from(page)} 관용구를 따라 DTO 정적 팩터리에 둔다.
 * <b>fetch join으로 연관이 초기화된 엔티티만 넘길 것</b> - 트랜잭션 밖에서 부르면 지연로딩이 터진다.
 *
 * <p>null 규칙(§1.2): {@code operatingHoursText}가 null이면 상시 개방이라 화면이 운영시간 줄 자체를 그리지 않는다 -
 * 빈 문자열로 바꾸지 말 것. {@code parkingAvailable}/{@code toiletAvailable}도 미조사는 null이지 false가 아니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceDetailResponse {

    private final Long id;
    private final String name;

    private final String regionCode;
    private final String regionName;
    private final String categoryCode;
    private final String categoryName;

    private final String roadAddress;
    private final String lotAddress;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    /** KTO 소개글. 미수집이면 null. */
    private final String overview;
    private final String phone;
    /** null = 상시 개방. */
    private final String operatingHoursText;
    private final String restDayText;

    private final Boolean parkingAvailable;
    private final Boolean toiletAvailable;

    /** 상세는 CLOSED도 내려간다 - 화면이 '폐업'으로 표시한다. UNKNOWN은 '미수집'이지 OPEN 추정이 아니다. */
    private final BusinessStatus businessStatus;

    private final boolean goodPrice;
    /** 착한가격업소 지정 기준일(행안부 발표월). 데이터 신선도 근거라 감추지 않는다. */
    private final LocalDate goodPriceBaseDate;

    private final boolean hiddenGem;
    /** 아직 계산 전이면 null - 0점으로 채우지 않는다. */
    private final BigDecimal hiddenGemScore;

    /** 별점 후기가 없으면 null. {@link #ratingAvgOrNull(Place)} 참고. */
    private final BigDecimal ratingAvg;
    private final int reviewCount;

    public static PlaceDetailResponse from(Place place) {
        return PlaceDetailResponse.builder()
                .id(place.getId())
                .name(place.getName())
                .regionCode(place.getRegion().getCode())
                .regionName(place.getRegion().getName())
                .categoryCode(place.getPrimaryCategory().getCode())
                .categoryName(place.getPrimaryCategory().getName())
                .roadAddress(place.getRoadAddress())
                .lotAddress(place.getLotAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .overview(place.getOverview())
                .phone(place.getPhone())
                .operatingHoursText(place.getOperatingHoursText())
                .restDayText(place.getRestDayText())
                .parkingAvailable(place.getParkingAvailable())
                .toiletAvailable(place.getToiletAvailable())
                .businessStatus(place.getBusinessStatus())
                .goodPrice(place.isGoodPrice())
                .goodPriceBaseDate(place.getGoodPriceBaseDate())
                .hiddenGem(place.isHiddenGem())
                .hiddenGemScore(place.getHiddenGemScore())
                .ratingAvg(ratingAvgOrNull(place))
                .reviewCount(place.getReviewCount())
                .build();
    }

    /**
     * {@code rating_avg}는 NOT NULL이라 후기가 0건이어도 0.00이 저장돼 있다(별점 0 후기는 평균에서 제외 - §2.4).
     * 그대로 내리면 화면에 '별 0점'이라는 없는 정보가 생기므로 null로 돌려 '정보 없음' 경로를 타게 한다(§1.2).
     * {@code BigDecimal.ZERO}는 scale이 0이라 DB 왕복 값(0.00)과 {@code equals}가 false다 - 비교는 {@code compareTo}(§9.1).
     */
    private static BigDecimal ratingAvgOrNull(Place place) {
        BigDecimal ratingAvg = place.getRatingAvg();
        return (ratingAvg == null || ratingAvg.compareTo(BigDecimal.ZERO) == 0) ? null : ratingAvg;
    }
}
