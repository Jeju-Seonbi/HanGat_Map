package com.example.hangat.course.model.enums;

/**
 * 이동수단 - 테이블 명세서 21.0 courses.transport / 19.0 course_presets.default_transport.
 *
 * <p>코스 생성 엔진이 이동시간·동선 계산의 전제로 쓴다(CRS 조건 입력).
 */
public enum Transport {

    RENTAL_CAR("렌터카"),
    PUBLIC_TRANSIT("대중교통"),
    TAXI("택시"),
    WALK_BIKE("도보·자전거");

    private final String label;

    Transport(String label) {
        this.label = label;
    }

    /** 화면 표시용 한글 라벨. */
    public String label() {
        return label;
    }
}
