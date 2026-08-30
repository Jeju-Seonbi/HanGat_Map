package com.example.hangat.course.model.enums;

/**
 * 코스 생성 사유 - 테이블 명세서 21.0 courses.generation_reason.
 *
 * <p>같은 조건의 재생성(USER_REGENERATE)과 날씨 재편성(WEATHER_REPLAN)을 구분해야
 * "왜 코스가 바뀌었는지"를 화면·알림에서 설명할 수 있다.
 */
public enum GenerationReason {

    /** 최초 생성. 기본값. */
    INITIAL,

    /** 사용자가 다시 생성 버튼을 눌렀다. */
    USER_REGENERATE,

    /** 날씨 예보 변화로 실내 대안 재편성(내일정 예보변경 알림 흐름). */
    WEATHER_REPLAN,

    /** 메인 샘플 코스 야간 배치. */
    SAMPLE_BATCH
}
