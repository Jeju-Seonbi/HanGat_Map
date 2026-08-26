package com.example.hangat.course.ai;

import com.example.hangat.course.model.CongestionLevel;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.Transport;
import com.example.hangat.course.travel.DistanceCalculationMethod;
import com.example.hangat.course.weather.CourseWeatherDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseAiInputDto(
        String contractVersion,
        TripConditionDto tripCondition,
        UserPreferencesDto userPreferences,
        List<CandidateFactDto> candidates,
        List<TravelFactDto> travelFacts,
        GenerationMetadataDto generationMetadata
) {

    public CourseAiInputDto {
        candidates = immutableList(candidates);
        travelFacts = immutableList(travelFacts);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TripConditionDto(
            LocalDate startDate,
            LocalDate endDate,
            Integer people,
            Integer budgetTotal,
            Transport transport
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UserPreferencesDto(
            List<SelectedRegionDto> selectedRegions,
            List<SelectedStyleDto> selectedStyles,
            List<PlaceConstraintDto> requiredPlaces,
            List<PlaceConstraintDto> forbiddenPlaces,
            AccommodationFactDto accommodation
    ) {
        public UserPreferencesDto {
            selectedRegions = immutableList(selectedRegions);
            selectedStyles = immutableList(selectedStyles);
            requiredPlaces = immutableList(requiredPlaces);
            forbiddenPlaces = immutableList(forbiddenPlaces);
        }
    }

    public record SelectedRegionDto(Long regionId, String code, String name) {
    }

    public record SelectedStyleDto(Long tagId, String code, String name, BigDecimal weight) {
    }

    /**
     * candidateId is an input-scoped response handle. It is never interchangeable with the
     * internal places.id or the durable sourceCode/sourcePlaceId pair.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlaceIdentityDto(
            String candidateId,
            Long placeId,
            String sourceCode,
            String sourcePlaceId
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlaceConstraintDto(
            PlaceIdentityDto identity,
            String name,
            String address,
            Double latitude,
            Double longitude,
            String categoryName,
            PreferenceType preferenceType,
            LocalDate fixedDate,
            LocalTime fixedTime
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AccommodationFactDto(
            PlaceIdentityDto identity,
            String name,
            String address,
            String roadAddress,
            Double latitude,
            Double longitude,
            String categoryName,
            String regionCode
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CandidateFactDto(
            PlaceIdentityDto identity,
            String name,
            String address,
            Double latitude,
            Double longitude,
            TourCategoryDto tourCategory,
            String regionCode,
            PreferenceType preferenceType,
            List<String> confirmedStyleHints,
            List<CongestionFactDto> congestion,
            List<CourseWeatherDto> weather
    ) {
        public CandidateFactDto {
            confirmedStyleHints = immutableList(confirmedStyleHints);
            congestion = immutableList(congestion);
            // null means weather facts were not supplied; an empty list means supplied but no
            // matching forecast rows were available.
            weather = weather == null ? null : List.copyOf(weather);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TourCategoryDto(String category1, String category2, String category3) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CongestionFactDto(
            LocalDate date,
            BigDecimal rate,
            CongestionLevel level
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TravelFactDto(
            String fromCandidateId,
            String fromTitle,
            String toCandidateId,
            String toTitle,
            BigDecimal straightDistanceKm,
            DistanceCalculationMethod straightDistanceMethod,
            BigDecimal routeDistanceKm,
            Integer durationMinutes,
            Transport transport,
            String routeSourceCode,
            Instant routeCalculatedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GenerationMetadataDto(
            GenerationReason generationReason,
            String algorithmVersion,
            String requestReference
    ) {
    }
}
