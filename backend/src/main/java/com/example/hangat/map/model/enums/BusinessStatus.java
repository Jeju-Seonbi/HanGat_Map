package com.example.hangat.map.model.enums;

/**
 * 장소 영업 상태 - 테이블 명세서 9.0 {@code places.business_status}
 *
 * <p>{@code @Enumerated(STRING)}으로 저장하므로 <b>상수 이름이 곧 DB 값</b>이다. 이름을 바꾸면 기존 데이터가 깨진다.
 * 미수집은 OPEN으로 낙관 추정하지 않고 UNKNOWN으로 둔다(설계서 §1.2 데이터 정직성).
 */
public enum BusinessStatus {
    OPEN,
    TEMP_CLOSED,
    CLOSED,
    UNKNOWN
}
