package com.example.hangat.course;

import com.example.hangat.course.facts.CourseCandidate;
import com.example.hangat.course.facts.CourseGenerationFacts;
import com.example.hangat.course.facts.TravelFact;
import com.example.hangat.course.facts.WeatherFactSet;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.PlacePreferenceDto;
import com.example.hangat.course.travel.CourseTravelLegDto;
import com.example.hangat.course.weather.CourseWeatherFacts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the provider-neutral fact snapshot consumed by the current AI compatibility bridge. */
final class CourseGenerationFactsAssembler {

    Assembly assemble(
            CourseRequestDto request,
            List<CourseCandidateDto> providerCandidates,
            CourseWeatherFacts weatherFacts,
            List<CourseTravelLegDto> travelLegs
    ) {
        CourseCandidateNormalizer.NormalizationResult normalized =
                new CourseCandidateNormalizer().normalize(request, providerCandidates);
        CourseWeatherFacts safeWeather = weatherFacts == null
                ? CourseWeatherFacts.empty()
                : weatherFacts;

        Set<String> candidateIds = normalized.candidates().stream()
                .map(candidate -> candidate.identity().candidateId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, WeatherFactSet> weatherSetsById = indexWeatherSets(
                safeWeather.weatherFactSets());
        validateWeatherReferences(
                safeWeather.weatherFactSetIdsByCandidateId(), candidateIds, weatherSetsById.keySet());

        List<CourseCandidate> enrichedCandidates = normalized.candidates().stream()
                .map(candidate -> withWeatherFactSetId(
                        candidate,
                        safeWeather.weatherFactSetIdsByCandidateId().get(
                                candidate.identity().candidateId())))
                .toList();
        List<TravelFact> normalizedTravelFacts = normalizeTravelFacts(travelLegs, candidateIds);

        return new Assembly(
                new CourseGenerationFacts(
                        enrichedCandidates,
                        safeWeather.weatherFactSets(),
                        normalizedTravelFacts),
                normalized.candidateIdsByPreference());
    }

    private Map<String, WeatherFactSet> indexWeatherSets(List<WeatherFactSet> weatherFactSets) {
        Map<String, WeatherFactSet> result = new HashMap<>();
        for (WeatherFactSet weatherFactSet : weatherFactSets) {
            if (weatherFactSet == null) {
                throw new IllegalArgumentException("날씨 fact set은 null일 수 없습니다.");
            }
            WeatherFactSet previous = result.putIfAbsent(
                    weatherFactSet.weatherFactSetId(), weatherFactSet);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "중복된 weatherFactSetId입니다: " + weatherFactSet.weatherFactSetId());
            }
        }
        return Map.copyOf(result);
    }

    private void validateWeatherReferences(
            Map<String, String> references,
            Set<String> candidateIds,
            Set<String> weatherFactSetIds
    ) {
        for (Map.Entry<String, String> entry : references.entrySet()) {
            if (!candidateIds.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "날씨 정보가 존재하지 않는 후보를 참조합니다: " + entry.getKey());
            }
            if (entry.getValue() == null || !weatherFactSetIds.contains(entry.getValue())) {
                throw new IllegalArgumentException(
                        "후보가 존재하지 않는 weather fact set을 참조합니다: " + entry.getValue());
            }
        }
    }

    private CourseCandidate withWeatherFactSetId(
            CourseCandidate candidate,
            String weatherFactSetId
    ) {
        return new CourseCandidate(
                candidate.identity(),
                candidate.place(),
                candidate.userConstraint(),
                candidate.regionCode(),
                candidate.externalClassifications(),
                candidate.internalPlaceCategory(),
                candidate.styleHints(),
                candidate.congestionFacts(),
                weatherFactSetId);
    }

    private List<TravelFact> normalizeTravelFacts(
            List<CourseTravelLegDto> travelLegs,
            Set<String> candidateIds
    ) {
        if (travelLegs == null || travelLegs.isEmpty()) {
            return List.of();
        }
        List<TravelFact> result = new ArrayList<>();
        Set<TravelPair> pairs = new HashSet<>();
        for (CourseTravelLegDto leg : travelLegs) {
            if (leg == null) {
                continue;
            }
            requireCandidate(leg.fromCandidateId(), candidateIds);
            requireCandidate(leg.toCandidateId(), candidateIds);
            TravelPair pair = new TravelPair(leg.fromCandidateId(), leg.toCandidateId());
            if (!pairs.add(pair)) {
                throw new IllegalArgumentException("중복된 후보 이동정보입니다: " + pair);
            }
            result.add(new TravelFact(
                    leg.fromCandidateId(),
                    leg.toCandidateId(),
                    kilometersToMeters(leg.straightDistanceKm()),
                    leg.straightDistanceMethod().name(),
                    kilometersToMeters(leg.routeDistanceKm()),
                    leg.durationMinutes(),
                    leg.transport(),
                    leg.routeSourceCode(),
                    leg.routeCalculatedAt()));
        }
        return List.copyOf(result);
    }

    private BigDecimal kilometersToMeters(BigDecimal kilometers) {
        return kilometers == null ? null : kilometers.movePointRight(3);
    }

    private void requireCandidate(String candidateId, Set<String> candidateIds) {
        if (candidateId == null || candidateId.isBlank() || !candidateIds.contains(candidateId)) {
            throw new IllegalArgumentException(
                    "이동정보가 존재하지 않는 후보를 참조합니다: " + candidateId);
        }
    }

    record Assembly(
            CourseGenerationFacts facts,
            Map<PlacePreferenceDto, String> candidateIdsByPreference
    ) {
    }

    private record TravelPair(String fromCandidateId, String toCandidateId) {
    }
}
