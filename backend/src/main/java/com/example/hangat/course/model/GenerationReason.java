package com.example.hangat.course.model;

public enum GenerationReason {
    INITIAL,
    // 사용자가 처음 코스 생성
    USER_REGENERATE,
    // 사용자가 조건을 바꿔서 다시 생성
    WEATHER_REPLAN,
    // 날씨 때문에 코스를 재구성
    SAMPLE_BATCH
    // 샘플 코스를 일괄 생성
}
