package com.example.hangat.map.model.dto;

import com.example.hangat.map.model.enums.BusinessStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 지도 마커·목록 한 건 ({@code GET /api/places}) - 프론트 {@code data/placesMap.js} 한 줄을 그대로 대체한다(설계서 §1.2).
 *
 * <p>필드 집합은 §1.2 계약의 n·x·y·r·c·addr·tel·hours·park·wc를 전부 덮는다. 하나라도 빠지면 프론트가 마커 82개마다
 * 상세를 호출하게 되므로(HTTP N+1) 임의로 줄이지 말 것. 반대로 {@code overview}는 없다 - H2에서 CLOB이라
 * 목록 쿼리가 아예 SELECT하지 않는다(§9.1 확정).
 *
 * <p><b>이 생성자는 {@code PlaceRepository.LIST_SELECT}의 JPQL이 리플렉션으로 직접 호출한다.</b>
 * 그래서 팀 기본형인 {@code @AllArgsConstructor(access = PRIVATE)}를 쓸 수 없고 public 생성자가 필요하다.
 * 순서를 바꾸면 타입이 다를 때는 부팅에서 죽고, <b>타입이 같을 때는 값이 조용히 밀린다</b> -
 * 후자를 막는 유일한 안전장치가 {@code PlaceRepositoryTest.목록_projection이_필드를_뒤바꾸지_않는다()}다.
 * 필드를 고칠 때는 리포지토리·테스트를 같은 커밋에서 함께 고칠 것.
 *
 * <p>값이 없는 필드는 null 그대로 내려보낸다(§1.2) - 0이나 false로 채우지 않는다.
 */
@Getter
public class PlaceListResponse {

    /** 혼잡 예보({@code GET /api/crowd/forecast}) {@code values}의 키가 이 id다(§2.2). */
    private final Long id;
    private final String name;

    /** 프론트 r = regionName. code는 권역 필터·지도 이동 키로 쓴다 - 표시명이 바뀌어도 필터가 안 깨지도록 둘 다 내린다. */
    private final String regionCode;
    private final String regionName;

    /** 프론트 c의 대분류. 세부분류(오름/해변/전시)는 아직 컬럼이 없다(§10-③). */
    private final String categoryCode;
    private final String categoryName;

    /** 프론트 addr. 오름·해안은 지번만 있는 곳이 많아 둘 다 내리고 화면이 있는 쪽을 고른다. */
    private final String roadAddress;
    private final String lotAddress;

    /** 프론트 y / x. 좌표 미수집이면 null이고 화면이 마커를 그리지 않는다. */
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    /** 프론트 tel / hours. hours가 null이면 상시 개방이라 빈 문자열로 바꾸지 말 것(§1.2). */
    private final String phone;
    private final String operatingHoursText;

    /** 프론트 park / wc. 미조사는 null이며 '없음'(false)과 구분된다. */
    private final Boolean parkingAvailable;
    private final Boolean toiletAvailable;

    /** CLOSED는 목록에서 빠지므로 여기 오는 값은 OPEN/TEMP_CLOSED/UNKNOWN이다. */
    private final BusinessStatus businessStatus;

    /** 착한가격업소·숨은명소는 별도 레이어라 type과 무관하게 항상 내린다. */
    private final boolean goodPrice;
    private final boolean hiddenGem;

    /** JPQL 전용 위치 생성자. 테스트·다른 코드는 순서 실수가 없도록 {@code builder()}를 쓴다. */
    @Builder
    public PlaceListResponse(Long id, String name,
                             String regionCode, String regionName,
                             String categoryCode, String categoryName,
                             String roadAddress, String lotAddress,
                             BigDecimal latitude, BigDecimal longitude,
                             String phone, String operatingHoursText,
                             Boolean parkingAvailable, Boolean toiletAvailable,
                             BusinessStatus businessStatus, boolean goodPrice, boolean hiddenGem) {
        this.id = id;
        this.name = name;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.operatingHoursText = operatingHoursText;
        this.parkingAvailable = parkingAvailable;
        this.toiletAvailable = toiletAvailable;
        this.businessStatus = businessStatus;
        this.goodPrice = goodPrice;
        this.hiddenGem = hiddenGem;
    }
}
