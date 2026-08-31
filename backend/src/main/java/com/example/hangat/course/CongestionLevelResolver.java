package com.example.hangat.course;

import com.example.hangat.course.model.CongestionLevel;

import java.math.BigDecimal;
import java.util.Optional;

public final class CongestionLevelResolver {

    private static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    private static final BigDecimal COMFORTABLE_UPPER_BOUND = new BigDecimal("33.33");
    private static final BigDecimal CROWDED_LOWER_BOUND = new BigDecimal("66.67");
    private static final BigDecimal MAX_RATE = new BigDecimal("100");

    private CongestionLevelResolver() {
    }

    public static Optional<CongestionLevel> resolve(String cnctrRate) {
        if (cnctrRate == null || cnctrRate.isBlank()) {
            return Optional.empty();
        }

        final BigDecimal rate;

        try {
            rate = new BigDecimal(cnctrRate.trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
            return Optional.empty();
        }

        if (rate.compareTo(COMFORTABLE_UPPER_BOUND) < 0) {
            return Optional.of(CongestionLevel.QUIET);
        }

        if (rate.compareTo(CROWDED_LOWER_BOUND) < 0) {
            return Optional.of(CongestionLevel.NORMAL);
        }

        return Optional.of(CongestionLevel.CROWDED);
    }
}
