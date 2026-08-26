package com.example.hangat.course.weather;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CourseWeatherDto(
        LocalDate forecastDate,
        LocalTime forecastTime,
        BigDecimal temperature,
        Integer precipitationProbability,
        String precipitationTypeCode,
        String skyConditionCode,
        BigDecimal windSpeed,
        Integer humidity
) {
}
