package com.example.hangat.course.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class CourseCandidateDto {

    private final TourPlaceDto place;
    private final List<CongestionDto> congestionData;
    private final PreferenceType preferenceType;
}
