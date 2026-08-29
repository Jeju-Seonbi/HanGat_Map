package com.example.hangat.course.facts;

import java.math.BigDecimal;

public record PlaceFact(
        String name,
        String address,
        String roadAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl
) {
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public PlaceFact {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("장소명은 필수입니다.");
        }
        requireRange(latitude, MIN_LATITUDE, MAX_LATITUDE, "위도");
        requireRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "경도");
    }

    private static void requireRange(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            String label
    ) {
        if (value != null
                && (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)) {
            throw new IllegalArgumentException(label + " 범위가 유효하지 않습니다.");
        }
    }
}
