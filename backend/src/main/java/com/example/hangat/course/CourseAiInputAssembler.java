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
import com.example.hangat.course.facts.CongestionFact;
import com.example.hangat.course.facts.CourseCandidate;
import com.example.hangat.course.facts.ExternalClassificationFact;
import com.example.hangat.course.model.AccommodationDto;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRegionDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.CourseStyleDto;
import com.example.hangat.course.model.PlacePreferenceDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.travel.CourseTravelLegDto;
import com.example.hangat.course.weather.CourseWeatherDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseAiInputAssembler {

    public static final String CONTRACT_VERSION = "1.0";
    public static final String UNKNOWN_REGION = "UNKNOWN";
    public static final String KTO_SOURCE_CODE = "KTO";
    public static final String KAKAO_SOURCE_CODE = "KAKAO_LOCAL";
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

        CandidateAssembly candidateAssembly = toCandidateFacts(
                request, safeCandidates, safeWeather);

        return new CourseAiInputDto(
                CONTRACT_VERSION,
                toTripCondition(request),
                toUserPreferences(request, candidateAssembly.candidateIdsByPreference()),
                candidateAssembly.candidates(),
                toTravelFacts(travelFacts, candidateAssembly.candidates()),
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

    private UserPreferencesDto toUserPreferences(
            CourseRequestDto request,
            Map<PlacePreferenceDto, String> candidateIdsByPreference
    ) {
        List<PlaceConstraintDto> requiredPlaces = new ArrayList<>();
        List<PlaceConstraintDto> forbiddenPlaces = new ArrayList<>();

        if (request.getCoursePlacePreferences() != null) {
            for (PlacePreferenceDto preference : request.getCoursePlacePreferences()) {
                if (preference == null || preference.getPreferenceType() == null) {
                    continue;
                }
                PlaceConstraintDto constraint = toPlaceConstraint(
                        preference, candidateIdsByPreference.get(preference));
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

    private PlaceConstraintDto toPlaceConstraint(
            PlacePreferenceDto preference,
            String candidateId
    ) {
        return new PlaceConstraintDto(
                new PlaceIdentityDto(
                        candidateId,
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

    private CandidateAssembly toCandidateFacts(
            CourseRequestDto request,
            List<CourseCandidateDto> candidates,
            Map<String, List<CourseWeatherDto>> weatherByCandidateId
    ) {
        CourseCandidateNormalizer.NormalizationResult normalized =
                new CourseCandidateNormalizer().normalize(request, candidates);
        List<CandidateFactDto> results = new ArrayList<>();
        Map<String, CourseCandidateDto> legacyCandidatesById = legacyCandidatesById(candidates);

        for (CourseCandidate candidate : normalized.candidates()) {
            String candidateId = candidate.identity().candidateId();

            List<CourseWeatherDto> weather = weatherByCandidateId.containsKey(candidateId)
                    ? copyNullable(weatherByCandidateId.get(candidateId))
                    : null;
            CourseCandidateDto legacyCandidate = legacyCandidatesById.get(candidateId);
            List<CongestionFactDto> congestion = KTO_SOURCE_CODE.equals(
                    candidate.identity().sourceCode()) && legacyCandidate != null
                    ? toCongestionFacts(legacyCandidate.getCongestionData())
                    : toNormalizedCongestionFacts(candidate.congestionFacts());
            results.add(new CandidateFactDto(
                    new PlaceIdentityDto(
                            candidateId,
                            candidate.identity().placeId(),
                            candidate.identity().sourceCode(),
                            candidate.identity().sourcePlaceId()),
                    candidate.place().name(),
                    firstNonBlank(candidate.place().roadAddress(), candidate.place().address()),
                    doubleValue(candidate.place().latitude()),
                    doubleValue(candidate.place().longitude()),
                    toTourCategory(candidate.externalClassifications()),
                    candidate.regionCode(),
                    candidate.userConstraint().preferenceType(),
                    candidate.styleHints().stream().map(style -> style.styleCode()).toList(),
                    congestion,
                    weather
            ));
        }

        return new CandidateAssembly(
                List.copyOf(results), normalized.candidateIdsByPreference());
    }

    private Map<String, CourseCandidateDto> legacyCandidatesById(
            List<CourseCandidateDto> candidates
    ) {
        Map<String, CourseCandidateDto> result = new LinkedHashMap<>();
        for (CourseCandidateDto candidate : candidates) {
            if (candidate != null && candidate.getPlace() != null
                    && candidate.getPlace().getContentId() != null) {
                result.putIfAbsent(candidate.getPlace().getContentId(), candidate);
            }
        }
        return result;
    }

    private TourCategoryDto toTourCategory(
            List<ExternalClassificationFact> classifications
    ) {
        return classifications.stream()
                .filter(classification -> KTO_SOURCE_CODE.equals(classification.sourceCode()))
                .findFirst()
                .map(classification -> new TourCategoryDto(
                        classification.level1Code(),
                        classification.level2Code(),
                        classification.level3Code()))
                .orElse(null);
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

    private List<CongestionFactDto> toNormalizedCongestionFacts(
            List<CongestionFact> congestionFacts
    ) {
        return congestionFacts.stream()
                .map(fact -> new CongestionFactDto(fact.date(), fact.rate(), fact.level()))
                .toList();
    }

    private List<TravelFactDto> toTravelFacts(
            List<CourseTravelLegDto> travelFacts,
            List<CandidateFactDto> candidates
    ) {
        if (travelFacts == null) {
            return List.of();
        }
        Set<String> candidateIds = candidates.stream()
                .map(candidate -> candidate.identity().candidateId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        // The caller supplies only useful pairs. The assembler intentionally does not create an
        // unconditional N x N matrix.
        return travelFacts.stream()
                .filter(fact -> fact != null)
                .map(fact -> {
                    requireTravelCandidate(fact.fromCandidateId(), candidateIds);
                    requireTravelCandidate(fact.toCandidateId(), candidateIds);
                    return new TravelFactDto(
                            fact.fromCandidateId(), fact.fromTitle(),
                            fact.toCandidateId(), fact.toTitle(),
                            fact.straightDistanceKm(), fact.straightDistanceMethod(),
                            fact.routeDistanceKm(), fact.durationMinutes(),
                            fact.transport(), fact.routeSourceCode(), fact.routeCalculatedAt());
                })
                .toList();
    }

    private void requireTravelCandidate(String candidateId, Set<String> candidateIds) {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("이동정보의 후보 식별자가 필요합니다.");
        }
        if (!candidateIds.contains(candidateId)) {
            throw new IllegalArgumentException(
                    "이동정보가 존재하지 않는 후보를 참조합니다: " + candidateId);
        }
    }

    private Double doubleValue(BigDecimal value) {
        return value == null ? null : value.doubleValue();
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

    private record CandidateAssembly(
            List<CandidateFactDto> candidates,
            Map<PlacePreferenceDto, String> candidateIdsByPreference
    ) {
    }
}
