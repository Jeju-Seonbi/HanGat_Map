package com.example.hangat.map.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KTO {@code areaBasedList2} 응답 항목 - 목록 적재에 쓰는 필드만 담는다.
 *
 * <p><b>모든 값이 문자열로 온다</b>(2026-08-23 실측). 좌표도 {@code "126.8606961680"} 처럼
 * 문자열이라 저장 전에 숫자로 바꿔야 한다.
 *
 * <p>⚠️ <b>{@code mapx}가 경도, {@code mapy}가 위도다.</b> x/y 순서로 읽으면 뒤집히는데
 * 컴파일도 되고 에러도 안 나서, 제주 전역 핀이 통째로 엉뚱한 곳에 찍혀야 알게 된다.
 *
 * <p>모르는 필드에 파싱이 깨지지 않게 {@code ignoreUnknown}을 켠다 - 포털이 응답 필드를
 * 예고 없이 추가하는 일이 있다. 빈 문자열은 {@code application.yaml}의
 * {@code accept-empty-string-as-null-object} 설정이 null로 바꿔 준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoPlaceItem(

        /** KTO 고유 ID. {@code place_source_mappings.source_place_id}가 된다. */
        String contentid,

        /** 12 관광지 / 14 문화시설 / 15 축제공연 / 28 레포츠 / 32 숙박 / 38 쇼핑 / 39 음식점 */
        String contenttypeid,

        String title,

        /** 도로명 또는 지번. KTO가 둘을 구분해 주지 않는다. */
        String addr1,

        /** 상세주소(동/호수 등). 있으면 addr1 뒤에 이어 붙인다. */
        String addr2,

        /** ★ 경도 (126.x) */
        String mapx,

        /** ★ 위도 (33.x) */
        String mapy,

        String tel,

        /** 대표 이미지 원본. 사진 적재(커밋 2-3)에서 쓴다. */
        String firstimage,

        /** 대표 이미지 썸네일. */
        String firstimage2,

        /** 이미지 저작권 구분(Type1/Type3). 푸터 출처 표기 요건과 직결된다. */
        String cpyrhtDivCd,

        /** 최종 수정 시각 (YYYYMMDDHHmmss). 변경 감지에 쓴다. */
        String modifiedtime,

        /** 분류체계 대분류 코드 (예: {@code NA} 자연관광). 세부 분류 태그의 상위 맥락. */
        String lclsSystm1,

        /** 분류체계 중분류 코드 (예: {@code NA01} 자연관광지). */
        String lclsSystm2,

        /**
         * ★ 분류체계 소분류 코드 (예: {@code NA010100} "산, 고개, 오름, 봉우리").
         *
         * <p>화면의 "모든 종류의 관광지" 드롭다운이 이 값으로 채워진다. 별도 API를 부르지 않아도
         * 목록 응답에 이미 들어 있다(2026-08-24 실측 - 가마오름 = NA / NA01 / NA010100).
         * 이름은 {@code lclsSystmCode2}가 준다.
         */
        String lclsSystm3
) {
}
