package com.example.hangat.course.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class CourseRequestDto {

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer people;
    private Integer budgetTotal;

    private List<String> regions;

    private Transport transport;

    private List<String> styles;

    private List<PlacePreferenceDto> coursePlacePreferences;

    private AccommodationDto accommodation;
}
