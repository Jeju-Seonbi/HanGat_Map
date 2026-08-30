package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.model.enums.CourseType;
import com.example.hangat.course.model.enums.GenerationReason;
import com.example.hangat.course.model.enums.Transport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
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
        Short people,
        Integer budgetTotal,
        Transport transport,
        AccommodationDto accommodation,
        List<DayDto> days,
        @JsonInclude(JsonInclude.Include.NON_NULL) String claimToken,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant claimExpiresAt
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
                null,
                days,
                null,
                null);
    }

    public CourseResponseDto(
            Long id,
            String contractVersion,
            CourseType courseType,
            GenerationReason generationReason,
            CourseStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Short people,
            Integer budgetTotal,
            Transport transport,
            AccommodationDto accommodation,
            List<DayDto> days
    ) {
        this(id, contractVersion, courseType, generationReason, status, startDate, endDate,
                people, budgetTotal, transport, accommodation, days, null, null);
    }

    public CourseResponseDto withClaimProof(String token, Instant expiresAt) {
        return new CourseResponseDto(id, contractVersion, courseType, generationReason, status,
                startDate, endDate, people, budgetTotal, transport, accommodation, days,
                token, expiresAt);
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
            String sourceCode,
            String sourcePlaceId,
            String placeName,
            String address,
            String roadAddress,
            Double latitude,
            Double longitude,
            String imageUrl,
            String categoryName,
            String regionCode,
            PreferenceType preferenceType,
            List<String> confirmedStyleHints,
            int dayNo,
            int position,
            LocalDate visitDate,
            LocalTime startTime,
            ItemSource itemSource,
            String recommendationReason,
            List<CourseItemCostDto> costs,
            BigDecimal inboundDistanceM,
            Integer inboundTravelMinutes,
            BigDecimal congestionRate,
            CongestionLevel congestionLevel,
            List<CongestionFactDto> congestion,
            List<WeatherFactDto> weather
    ) {
        public ItemDto {
            confirmedStyleHints = immutableList(confirmedStyleHints);
            costs = immutableList(costs);
            congestion = immutableList(congestion);
            weather = weather == null ? null : List.copyOf(weather);
        }

    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CourseItemCostDto(
            Long id,
            Long courseId,
            Long courseItemId,
            String category,
            String accuracyType,
            BigDecimal amountMin,
            BigDecimal amountMax,
            String currency,
            String basisText
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
