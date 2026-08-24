package com.example.hangat.map.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KTO {@code tatsCnctrRatedList} 응답 항목 - 관광지별 집중률.
 *
 * <p>2026-08-24 실측: 제주 전역 9,856행 / 22일치 / 관광지 448곳.
 *
 * <p>⚠️ <b>고유 ID가 없다.</b> KorService2의 {@code contentid}에 해당하는 값이 응답에 없어서
 * {@code tAtsNm}(관광지명) 문자열로만 우리 장소와 이어붙일 수 있다(설계서 §3.6).
 * 같은 한국관광공사인데도 두 서비스가 장소 마스터를 공유하지 않아, 같은 곳을 다르게 부른다 -
 * 집중률 "주상절리대" = KorService2 "대포주상절리". 그래서 매칭률이 100%가 될 수 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CnctrRateItem(

        /** 예보 대상일 {@code yyyyMMdd}. 제주 현지 기준일이다 - 저장 전 UTC로 바꾼다. */
        String baseYmd,

        /** 시도 코드. 제주는 {@code 50}. */
        String areaCd,

        /** ⚠️ 5자리다({@code 50110}). KorService2의 {@code lDongSignguCd}는 3자리({@code 110})라 섞으면 깨진다. */
        String signguCd,

        /** ★ 관광지명. 우리 {@code places.normalized_name}과 맞출 유일한 키. */
        String tAtsNm,

        /** 집중률 0~100. 그 장소 최성수기 대비 %다 - 장소 간 비교 금지(설계서 §3.5.1). */
        String cnctrRate
) {
}
