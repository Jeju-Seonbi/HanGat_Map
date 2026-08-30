package com.example.hangat.course.model.enums;

/**
 * 코스 일정 항목의 배치 출처 - 테이블 명세서 25.0 course_items.item_source.
 *
 * <p>화면이 "AI가 넣은 곳 / 내가 고정한 곳 / 내가 바꾼 곳"을 구분해 보여주는 근거다.
 * 특히 REPLACEMENT는 스왑(#과밀지역 우회)의 흔적이라 근거 문구가 "직접 바꾼 곳이에요"로 바뀐다.
 */
public enum CourseItemSource {

    /** 사용자가 조건 입력에서 고정한 일정(course_place_preferences의 WANT). */
    USER_FIXED,

    /** 엔진이 추천해 배치한 일정. 기본값. */
    AI_RECOMMENDED,

    /** 사용자가 스왑으로 교체한 일정 - replaced_from_place_id와 함께 남는다. */
    REPLACEMENT
}
