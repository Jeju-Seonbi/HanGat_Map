package com.example.hangat.course.weather;

import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;

import java.util.List;
import java.util.Map;

/**
 * Optional boundary for supplying already verified weather facts to AI input preparation.
 * No bean is registered until official region grids and forecast-base selection are available.
 */
public interface CourseWeatherFactsProvider {

    Map<String, List<CourseWeatherDto>> load(
            CourseRequestDto request,
            List<CourseCandidateDto> candidates
    );
}
