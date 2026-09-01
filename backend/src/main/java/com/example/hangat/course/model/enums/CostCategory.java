package com.example.hangat.course.model.enums;

/**
 * 코스 비용 구분 - 테이블 명세서 26.0 course_item_costs.category.
 */
public enum CostCategory {

    FOOD("식비"),
    LODGING("숙박"),
    TRANSPORT("교통"),
    ACTIVITY("입장·체험"),
    OTHER("기타");

    private final String label;

    CostCategory(String label) {
        this.label = label;
    }

    /** 화면 표시용 한글 라벨. */
    public String label() {
        return label;
    }
}
