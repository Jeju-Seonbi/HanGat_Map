package com.example.hangat.course.facts;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public record WeatherFactSet(
        String weatherFactSetId,
        String sourceCode,
        Integer gridX,
        Integer gridY,
        LocalDate baseDate,
        LocalTime baseTime,
        List<WeatherFact> facts
) {
    public WeatherFactSet {
        requireText(weatherFactSetId, "weatherFactSetId는 필수입니다.");
        requireText(sourceCode, "날씨 sourceCode는 필수입니다.");
        Objects.requireNonNull(gridX, "날씨 gridX는 필수입니다.");
        Objects.requireNonNull(gridY, "날씨 gridY는 필수입니다.");
        Objects.requireNonNull(baseDate, "날씨 발표일은 필수입니다.");
        Objects.requireNonNull(baseTime, "날씨 발표시각은 필수입니다.");
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
