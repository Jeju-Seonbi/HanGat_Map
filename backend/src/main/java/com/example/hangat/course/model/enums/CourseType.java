package com.example.hangat.course.model.enums;

/**
 * 코스 구분 - 테이블 명세서 21.0 courses.course_type.
 *
 * <p>명세서 CHECK: SAMPLE이면 preset_id·title 필수, user_id는 NULL이어야 한다.
 * DB CHECK는 재현하지 않으므로(§10-④) 이 불변식은 만드는 쪽(샘플 배치)이 지킨다.
 */
public enum CourseType {

    /** 사용자 조건으로 생성한 코스. 비회원 임시 코스도 USER다(user_id만 NULL). */
    USER,

    /** 메인 노출용 사전 생성 샘플 코스 - 프리셋 배치가 만든다. */
    SAMPLE
}
