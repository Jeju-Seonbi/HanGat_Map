package com.example.hangat.course;

final class KtoPlaceCategoryResolver {

    private static final String KTO_CAFE_CATEGORY = "A05020900";

    private KtoPlaceCategoryResolver() {
    }

    static String resolve(String category1, String category3) {
        if (KTO_CAFE_CATEGORY.equals(category3)) {
            return "CAFE";
        }
        if ("A05".equals(category1)) {
            return "FOOD";
        }
        if ("B02".equals(category1)) {
            return "LODGING";
        }
        if ("A01".equals(category1) || "A02".equals(category1)) {
            return "TOURIST";
        }
        throw new IllegalStateException(
                "내부 장소 카테고리로 확인할 수 없는 KTO 후보입니다: " + category1);
    }
}
