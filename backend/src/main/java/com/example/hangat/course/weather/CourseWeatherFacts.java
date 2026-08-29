package com.example.hangat.course.weather;

import com.example.hangat.course.facts.WeatherFactSet;

import java.util.List;
import java.util.Map;

/**
 * Verified weather facts and candidate references supplied to course generation.
 *
 * <p>The producer is responsible for selecting the KMA grid and issuance. This carrier does not
 * invent either policy when weather data is unavailable.</p>
 */
public record CourseWeatherFacts(
        Map<String, String> weatherFactSetIdsByCandidateId,
        List<WeatherFactSet> weatherFactSets
) {
    public CourseWeatherFacts {
        weatherFactSetIdsByCandidateId = weatherFactSetIdsByCandidateId == null
                ? Map.of()
                : Map.copyOf(weatherFactSetIdsByCandidateId);
        weatherFactSets = weatherFactSets == null ? List.of() : List.copyOf(weatherFactSets);
    }

    public static CourseWeatherFacts empty() {
        return new CourseWeatherFacts(Map.of(), List.of());
    }
}
