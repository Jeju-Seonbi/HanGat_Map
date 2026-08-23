package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class CourseRequestDto {

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    private Integer people;

    @JsonProperty("budget_total")
    private Integer budgetTotal;

    @JsonProperty("course_regions")
    private List<CourseRegionDto> courseRegions;

    private Transport transport;

    @JsonProperty("course_styles")
    private List<CourseStyleDto> courseStyles;

    @JsonProperty("course_place_preferences")
    private List<PlacePreferenceDto> coursePlacePreferences;

    private AccommodationDto accommodation;
}
