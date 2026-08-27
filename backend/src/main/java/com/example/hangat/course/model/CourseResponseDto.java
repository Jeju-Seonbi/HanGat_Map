package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseResponseDto(
        Long id,
        String contractVersion,
        CourseType courseType,
        GenerationReason generationReason,
        CourseStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Integer people,
        Integer budgetTotal,
        Transport transport,
        List<DayDto> days
) {
    public CourseResponseDto {
        days = immutableList(days);
    }

    public CourseResponseDto(
            String contractVersion,
            LocalDate startDate,
            LocalDate endDate,
            List<DayDto> days
    ) {
        this(
                null,
                contractVersion,
                null,
                null,
                null,
                startDate,
                endDate,
                null,
                null,
                null,
                days);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DayDto(
            int dayNo,
            LocalDate visitDate,
            List<ItemDto> items
    ) {
        public DayDto {
            items = immutableList(items);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemDto(
            Long id,
            Long courseId,
            Long placeId,
            String candidateId,
            String placeName,
            String address,
            Double latitude,
            Double longitude,
            String imageUrl,
            TourCategoryDto tourCategory,
            String regionCode,
            PreferenceType preferenceType,
            List<String> confirmedStyleHints,
            int position,
            LocalTime startTime,
            ItemSource itemSource,
            String recommendationReason,
            List<CongestionFactDto> congestion,
            List<WeatherFactDto> weather
    ) {
        public ItemDto {
            confirmedStyleHints = immutableList(confirmedStyleHints);
            congestion = immutableList(congestion);
            weather = weather == null ? null : List.copyOf(weather);
        }

        public ItemDto(
                String candidateId,
                String placeName,
                String address,
                Double latitude,
                Double longitude,
                String imageUrl,
                TourCategoryDto tourCategory,
                String regionCode,
                PreferenceType preferenceType,
                List<String> confirmedStyleHints,
                int position,
                LocalTime startTime,
                ItemSource itemSource,
                String recommendationReason,
                List<CongestionFactDto> congestion,
                List<WeatherFactDto> weather
        ) {
            this(
                    null,
                    null,
                    null,
                    candidateId,
                    placeName,
                    address,
                    latitude,
                    longitude,
                    imageUrl,
                    tourCategory,
                    regionCode,
                    preferenceType,
                    confirmedStyleHints,
                    position,
                    startTime,
                    itemSource,
                    recommendationReason,
                    congestion,
                    weather);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TourCategoryDto(
            String category1,
            String category2,
            String category3
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CongestionFactDto(
            LocalDate date,
            BigDecimal rate,
            CongestionLevel level
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WeatherFactDto(
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

    public enum ItemSource {
        USER_FIXED,
        AI_RECOMMENDED,
        REPLACEMENT
    }
}
