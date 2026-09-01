package com.example.hangat.course.facts;

import java.util.List;

public record CourseGenerationFacts(
        List<CourseCandidate> candidates,
        List<WeatherFactSet> weatherFactSets,
        List<TravelFact> travelFacts
) {
    public CourseGenerationFacts {
        candidates = immutableList(candidates);
        weatherFactSets = immutableList(weatherFactSets);
        travelFacts = immutableList(travelFacts);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
