package com.example.hangat.map.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KTO {@code lclsSystmCode2} 응답 항목 - 분류체계 코드표.
 *
 * <p>한 행이 "대분류 - 중분류 - 소분류" 한 갈래를 통째로 담는다. 그래서 소분류 하나를 얻으면
 * 상위 맥락도 같이 온다 - 별도 조회가 필요 없다.
 *
 * <p>2026-08-24 실측: 전체 246건이 한 번에 오고({@code numOfRows=1000}),
 * <b>소분류 코드·이름 모두 중복이 없다</b>. {@code tags}의 UNIQUE 제약이 그대로 성립한다는 뜻.
 * 대분류는 10개 - AC 숙박 / C01 추천코스 / EV 축제·공연·행사 / EX 체험관광 / FD 음식 /
 * HS 역사관광 / LS 레저스포츠 / NA 자연관광 / SH 쇼핑 / VE 문화관광.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LclsSystmItem(

        String lclsSystm1Cd,
        String lclsSystm1Nm,

        String lclsSystm2Cd,
        String lclsSystm2Nm,

        /** 소분류 코드 - {@code tags.code}가 된다. 실측 최대 9자. */
        String lclsSystm3Cd,

        /** 소분류 이름 - {@code tags.name}이 된다. 실측 최대 20자. */
        String lclsSystm3Nm
) {

    /** {@code tags.description}에 넣을 상위 맥락. 예: "자연관광 &gt; 자연경관". */
    public String hierarchyText() {
        if (lclsSystm1Nm == null) {
            return null;
        }
        return lclsSystm2Nm == null ? lclsSystm1Nm : lclsSystm1Nm + " > " + lclsSystm2Nm;
    }
}
