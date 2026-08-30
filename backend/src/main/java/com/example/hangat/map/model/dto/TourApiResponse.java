package com.example.hangat.map.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 한국관광공사 계열 API 공통 응답 껍데기.
 *
 * <p>KorService2(15101578)·관광지 집중률(15128555)·지역별 방문자수(15101972)가
 * <b>전부 이 모양</b>이다(2026-08-23 실호출로 확인). 그래서 제네릭 하나로 셋을 커버한다.
 *
 * <pre>
 * { "response": { "header": { "resultCode": "0000", "resultMsg": "OK" },
 *                 "body":   { "items": { "item": [ … ] },
 *                             "numOfRows": 1000, "pageNo": 1, "totalCount": 2147 } } }
 * </pre>
 *
 * <p>⚠️ 소상공인 상가정보(B553077)는 구조가 다르다 - {@code items}가 배열 직접이고
 * 쿼리 파라미터도 {@code _type}이 아니라 {@code type}이다. 그쪽은 별도 타입으로 다룬다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}를 붙인 이유: 포털이 응답 필드를
 * 예고 없이 추가하는 일이 있어, 모르는 필드에 파싱이 깨지지 않게 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiResponse<T>(Response<T> response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response<T>(Header header, Body<T> body) {
    }

    /** {@code resultCode}가 "0000"이 아니면 서비스 레벨 실패다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body<T>(Items<T> items, Integer numOfRows, Integer pageNo, Integer totalCount) {
    }

    /** 결과가 0건이면 {@code items}가 빈 문자열로 오는 경우가 있어 {@code item}이 null일 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items<T>(List<T> item) {
    }

    /** 응답에서 항목 목록만 꺼낸다. 어느 단계가 null이든 빈 리스트를 준다. */
    public List<T> items() {
        if (response == null || response.body() == null
                || response.body().items() == null || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }

    /** 전체 건수. 페이징 종료 조건에 쓴다. 알 수 없으면 0. */
    public int totalCount() {
        if (response == null || response.body() == null || response.body().totalCount() == null) {
            return 0;
        }
        return response.body().totalCount();
    }

    public String resultCode() {
        return (response == null || response.header() == null) ? null : response.header().resultCode();
    }

    public String resultMsg() {
        return (response == null || response.header() == null) ? null : response.header().resultMsg();
    }
}
