package com.example.hangat.course.weather;

import com.example.hangat.domain.weather.model.ShortTermItem;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CourseWeatherMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    private CourseWeatherMapper() {
    }

    static List<CourseWeatherDto> map(List<ShortTermItem> items, LocalDate targetDate) {
        if (items == null || items.isEmpty() || targetDate == null) {
            return Collections.emptyList();
        }

        Map<LocalTime, WeatherValues> valuesByTime = new LinkedHashMap<>();
        for (ShortTermItem item : items) {
            if (item == null || !targetDate.equals(parseDate(item.fcstDate()))
                    || !WeatherValues.supports(item.category())) {
                continue;
            }

            LocalTime forecastTime = parseTime(item.fcstTime());
            if (forecastTime == null) {
                continue;
            }

            WeatherValues values = valuesByTime.computeIfAbsent(forecastTime, ignored -> new WeatherValues());
            values.accept(item.category(), item.fcstValue());
        }

        return valuesByTime.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getValue().toDto(targetDate, entry.getKey()))
                .toList();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeException | NullPointerException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeException | NullPointerException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class WeatherValues {
        private BigDecimal temperature;
        private Integer precipitationProbability;
        private String precipitationTypeCode;
        private String skyConditionCode;
        private BigDecimal windSpeed;
        private Integer humidity;

        private static boolean supports(String category) {
            return "TMP".equals(category)
                    || "POP".equals(category)
                    || "PTY".equals(category)
                    || "SKY".equals(category)
                    || "WSD".equals(category)
                    || "REH".equals(category);
        }

        private void accept(String category, String value) {
            if (category == null) {
                return;
            }
            switch (category) {
                case "TMP" -> temperature = decimal(value);
                case "POP" -> precipitationProbability = integer(value);
                case "PTY" -> precipitationTypeCode = value;
                case "SKY" -> skyConditionCode = value;
                case "WSD" -> windSpeed = decimal(value);
                case "REH" -> humidity = integer(value);
                default -> { }
            }
        }

        private CourseWeatherDto toDto(LocalDate date, LocalTime time) {
            return new CourseWeatherDto(date, time, temperature, precipitationProbability,
                    precipitationTypeCode, skyConditionCode, windSpeed, humidity);
        }
    }
}
