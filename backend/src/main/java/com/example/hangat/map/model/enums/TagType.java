package com.example.hangat.map.model.enums;

/**
 * 태그 용도 - 테이블 명세서 7.0 {@code tags.tag_type}
 *
 * <p>장소 분류와 여행 스타일이 한 테이블을 공유하기 때문에 구분이 필요하다.
 * KTO 분류체계(lclsSystm)에서 오는 것은 전부 {@link #PLACE}다.
 */
public enum TagType {

    /** 장소 분류 전용 - 오름·해변·미술관 등. */
    PLACE,

    /** 여행 스타일 전용 - 코스 추천에서 쓴다(AI코스 담당 영역). */
    STYLE,

    /** 양쪽에 쓰이는 태그. */
    BOTH
}
