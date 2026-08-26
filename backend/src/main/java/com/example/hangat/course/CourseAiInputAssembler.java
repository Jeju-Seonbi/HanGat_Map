package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiInputDto.AccommodationFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.CongestionFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.GenerationMetadataDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceConstraintDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceIdentityDto;
import com.example.hangat.course.ai.CourseAiInputDto.SelectedRegionDto;
import com.example.hangat.course.ai.CourseAiInputDto.SelectedStyleDto;
import com.example.hangat.course.ai.CourseAiInputDto.TourCategoryDto;
import com.example.hangat.course.ai.CourseAiInputDto.TravelFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.TripConditionDto;
import com.example.hangat.course.ai.CourseAiInputDto.UserPreferencesDto;
import com.example.hangat.course.model.AccommodationDto;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRegionDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.CourseStyleDto;
import com.example.hangat.course.model.PlacePreferenceDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.travel.CourseTravelLegDto;
import com.example.hangat.course.weather.CourseWeatherDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseAiInputAssembler {

    public static final String CONTRACT_VERSION = "1.0";
    public static final String UNKNOWN_REGION = "UNKNOWN";

    private static final DateTimeFormatter CONGESTION_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    public CourseAiInputDto assemble(
            CourseRequestDto request,
            List<CourseCandidateDto> candidates,
            Map<String, List<CourseWeatherDto>> weatherByCandidateId,
            List<CourseTravelLegDto> travelFacts,
            GenerationMetadataDto generationMetadata
    ) {
        if (request == null) {
            throw new IllegalArgumentException("AI 코스 입력 요청이 필요합니다.");
        }
        if (generationMetadata == null || generationMetadata.generationReason() == null) {
            throw new IllegalArgumentException("AI 코스 생성 사유가 필요합니다.");
        }

        List<CourseCandidateDto> safeCandidates = candidates == null
                ? Collections.emptyList()
                : candidates;
        Map<String, List<CourseWeatherDto>> safeWeather = weatherByCandidateId == null
                ? Collections.emptyMap()
                : weatherByCandidateId;

        return new CourseAiInputDto(
                CONTRACT_VERSION,
                toTripCondition(request),
                toUserPreferences(request),
                toCandidateFacts(safeCandidates, safeWeather),
                toTravelFacts(travelFacts),
                generationMetadata
        );
    }

    private TripConditionDto toTripCondition(CourseRequestDto request) {
        return new TripConditionDto(
                request.getStartDate(),
                request.getEndDate(),
                request.getPeople(),
                request.getBudgetTotal(),
                request.getTransport()
        );
    }

    private UserPreferencesDto toUserPreferences(CourseRequestDto request) {
        List<PlaceConstraintDto> requiredPlaces = new ArrayList<>();
        List<PlaceConstraintDto> forbiddenPlaces = new ArrayList<>();

        if (request.getCoursePlacePreferences() != null) {
            for (PlacePreferenceDto preference : request.getCoursePlacePreferences()) {
                if (preference == null || preference.getPreferenceType() == null) {
                    continue;
                }
                PlaceConstraintDto constraint = toPlaceConstraint(preference);
                if (preference.getPreferenceType() == PreferenceType.WANT) {
                    requiredPlaces.add(constraint);
                } else if (preference.getPreferenceType() == PreferenceType.AVOID) {
                    forbiddenPlaces.add(constraint);
                }
            }
        }

        return new UserPreferencesDto(
                toSelectedRegions(request.getCourseRegions()),
                toSelectedStyles(request.getCourseStyles()),
                requiredPlaces,
                forbiddenPlaces,
                toAccommodation(request.getAccommodation())
        );
    }

    private List<SelectedRegionDto> toSelectedRegions(List<CourseRegionDto> regions) {
        if (regions == null) {
            return List.of();
        }
        return regions.stream()
                .filter(region -> region != null)
                .map(region -> new SelectedRegionDto(
                        region.getRegionId(), region.getCode(), region.getName()))
                .toList();
    }

    private List<SelectedStyleDto> toSelectedStyles(List<CourseStyleDto> styles) {
        if (styles == null) {
            return List.of();
        }
        return styles.stream()
                .filter(style -> style != null)
                .map(style -> new SelectedStyleDto(
                        style.getTagId(), style.getCode(), style.getName(), style.getWeight()))
                .toList();
    }

    private PlaceConstraintDto toPlaceConstraint(PlacePreferenceDto preference) {
        return new PlaceConstraintDto(
                new PlaceIdentityDto(
                        null,
                        preference.getPlaceId(),
                        preference.getSourceCode(),
                        preference.getSourcePlaceId()),
                preference.getPlaceName(),
                firstNonBlank(preference.getRoadAddress(), preference.getAddress()),
                preference.getLatitude(),
                preference.getLongitude(),
                preference.getCategoryName(),
                preference.getPreferenceType(),
                preference.getFixedDate(),
                preference.getFixedTime()
        );
    }

    private AccommodationFactDto toAccommodation(AccommodationDto accommodation) {
        if (accommodation == null) {
            return null;
        }
        return new AccommodationFactDto(
                new PlaceIdentityDto(
                        null,
                        accommodation.getPlaceId(),
                        accommodation.getSourceCode(),
                        accommodation.getSourcePlaceId()),
                accommodation.getPlaceName(),
                accommodation.getAddress(),
                accommodation.getRoadAddress(),
                accommodation.getLatitude(),
                accommodation.getLongitude(),
                accommodation.getCategoryName(),
                accommodation.getRegion()
        );
    }

    private List<CandidateFactDto> toCandidateFacts(
            List<CourseCandidateDto> candidates,
            Map<String, List<CourseWeatherDto>> weatherByCandidateId
    ) {
        List<CandidateFactDto> results = new ArrayList<>();
        Set<String> candidateIds = new HashSet<>();

        for (CourseCandidateDto candidate : candidates) {
            if (candidate == null || candidate.getPlace() == null
                    || candidate.getPreferenceType() == PreferenceType.AVOID) {
                continue;
            }
            TourPlaceDto place = candidate.getPlace();
            String candidateId = requireCandidateId(place.getContentId());
            if (!candidateIds.add(candidateId)) {
                throw new IllegalArgumentException("중복된 AI 후보 식별자입니다: " + candidateId);
            }

            List<CourseWeatherDto> weather = weatherByCandidateId.containsKey(candidateId)
                    ? copyNullable(weatherByCandidateId.get(candidateId))
                    : null;
            results.add(new CandidateFactDto(
                    new PlaceIdentityDto(candidateId, null, null, null),
                    place.getTitle(),
                    place.getAddress(),
                    place.getLatitude(),
                    place.getLongitude(),
                    new TourCategoryDto(
                            place.getCategory(), place.getCategory2(), place.getCategory3()),
                    TourPlaceRegionResolver.resolve(place.getAddress())
                            .map(Enum::name)
                            .orElse(UNKNOWN_REGION),
                    candidate.getPreferenceType(),
                    candidate.getConfirmedStyleHints(),
                    toCongestionFacts(candidate.getCongestionData()),
                    weather
            ));
        }
        return List.copyOf(results);
    }

    private List<CongestionFactDto> toCongestionFacts(List<CongestionDto> congestionData) {
        if (congestionData == null) {
            return List.of();
        }
        return congestionData.stream()
                .filter(congestion -> congestion != null)
                .map(congestion -> new CongestionFactDto(
                        parseCongestionDate(congestion.getBaseYmd()),
                        parseRate(congestion.getCnctrRate()),
                        CongestionLevelResolver.resolve(congestion.getCnctrRate()).orElse(null)))
                .toList();
    }

    private List<TravelFactDto> toTravelFacts(List<CourseTravelLegDto> travelFacts) {
        if (travelFacts == null) {
            return List.of();
        }
        // The caller supplies only useful pairs. The assembler intentionally does not create an
        // unconditional N x N matrix.
        return travelFacts.stream()
                .filter(fact -> fact != null)
                .map(fact -> new TravelFactDto(
                        fact.fromCandidateId(), fact.fromTitle(),
                        fact.toCandidateId(), fact.toTitle(),
                        fact.straightDistanceKm(), fact.straightDistanceMethod(),
                        fact.routeDistanceKm(), fact.durationMinutes(),
                        fact.transport(), fact.routeSourceCode(), fact.routeCalculatedAt()))
                .toList();
    }

    private String requireCandidateId(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("AI 후보 식별자가 필요합니다.");
        }
        return candidateId;
    }

    private LocalDate parseCongestionDate(String value) {
        try {
            return LocalDate.parse(value, CONGESTION_DATE_FORMATTER);
        } catch (DateTimeException | NullPointerException exception) {
            return null;
        }
    }

    private BigDecimal parseRate(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> List<T> copyNullable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
