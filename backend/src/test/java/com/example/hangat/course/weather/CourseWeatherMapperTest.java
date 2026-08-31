package com.example.hangat.course.weather;

import com.example.hangat.domain.weather.model.ShortTermItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseWeatherMapperTest {

    @Test
    void mapsOnlyTheRequestedDateAndGroupsValuesByForecastTime() {
        List<ShortTermItem> items = List.of(
                item("TMP", "20260827", "1200", "27.5"),
                item("POP", "20260827", "1200", "60"),
                item("PTY", "20260827", "1200", "1"),
                item("SKY", "20260827", "1200", "4"),
                item("WSD", "20260827", "1200", "3.2"),
                item("REH", "20260827", "1200", "80"),
                item("TMP", "20260827", "0900", "25"),
                item("TMP", "20260828", "1200", "29"));

        List<CourseWeatherDto> result = CourseWeatherMapper.map(items, LocalDate.of(2026, 8, 27));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).forecastTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(1)).isEqualTo(new CourseWeatherDto(
                LocalDate.of(2026, 8, 27),
                LocalTime.of(12, 0),
                new BigDecimal("27.5"),
                60,
                "1",
                "4",
                new BigDecimal("3.2"),
                80));
    }

    @Test
    void returnsEmptyForMissingInputOrDifferentDate() {
        assertThat(CourseWeatherMapper.map(null, LocalDate.of(2026, 8, 27))).isEmpty();
        assertThat(CourseWeatherMapper.map(List.of(), LocalDate.of(2026, 8, 27))).isEmpty();
        assertThat(CourseWeatherMapper.map(
                List.of(item("TMP", "20260828", "1200", "29")),
                LocalDate.of(2026, 8, 27))).isEmpty();
        assertThat(CourseWeatherMapper.map(
                List.of(item("VEC", "20260827", "1200", "180")),
                LocalDate.of(2026, 8, 27))).isEmpty();
    }

    @Test
    void skipsInvalidDateAndTimeAndKeepsUnknownNumericValueAsNull() {
        List<ShortTermItem> items = List.of(
                item("TMP", "invalid", "1200", "25"),
                item("TMP", "20260827", "invalid", "25"),
                item("TMP", "20260827", "1500", "not-a-number"),
                item("SKY", "20260827", "1500", "3"));

        List<CourseWeatherDto> result = CourseWeatherMapper.map(items, LocalDate.of(2026, 8, 27));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).temperature()).isNull();
        assertThat(result.get(0).skyConditionCode()).isEqualTo("3");
    }

    private static ShortTermItem item(String category, String date, String time, String value) {
        return new ShortTermItem(category, date, time, value);
    }
}
