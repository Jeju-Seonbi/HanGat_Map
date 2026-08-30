package com.example.hangat.map.model.enums;

/**
 * 태그가 붙은 경로 - 테이블 명세서 13.0 {@code place_tags.source_type}
 *
 * <p>같은 태그라도 출처에 따라 신뢰도가 다르다. 배치가 붙인 것은 다시 배치가 갱신해도 되지만
 * 사람이 손으로 붙인 것({@link #ADMIN})을 배치가 덮어쓰면 안 되므로, 나중에 갱신 로직에서 구분해야 한다.
 */
public enum TagSourceType {

    /** 외부 API가 준 분류 - KTO lclsSystm 등. */
    API,

    /** 후기 텍스트에서 뽑은 것. */
    REVIEW,

    /** 운영자가 손으로 붙인 것. 배치가 덮어쓰면 안 된다. */
    ADMIN,

    /** 추천 모델이 계산한 것. */
    MODEL
}
