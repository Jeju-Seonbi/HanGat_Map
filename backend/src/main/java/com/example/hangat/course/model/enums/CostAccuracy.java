package com.example.hangat.course.model.enums;

/**
 * 비용 산정 정확도 - 테이블 명세서 26.0 course_item_costs.accuracy_type.
 *
 * <p><b>정직성 원칙의 핵심 값이다.</b> VERIFIED("검증가")는 착한가격업소 메뉴 근거가 있을 때만 쓴다 -
 * TourAPI 음식점에는 구조화된 가격 필드가 없으므로 그 외는 전부 ESTIMATED("추정") 또는 UNKNOWN이다.
 * 화면 라벨("검증가"/"추정 범위"/"요금 확인 필요")이 이 값에서 나온다.
 */
public enum CostAccuracy {

    /** 착한가격업소 메뉴 등 실측 근거가 있는 확정가 - amount_min = amount_max. */
    VERIFIED,

    /** 추정 범위 - amount_min ≤ amount_max. */
    ESTIMATED,

    /** 산정 불가 - 금액 없이 "요금 확인 필요"로만 표시(명세서 CHECK: 금액 컬럼 NULL). */
    UNKNOWN
}
