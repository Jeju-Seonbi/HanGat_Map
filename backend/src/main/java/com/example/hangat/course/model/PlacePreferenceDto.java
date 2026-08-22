package com.example.hangat.course.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class PlacePreferenceDto {

    private Long placeId;

    private String sourceCode;
    private String sourcePlaceId;

    private String placeName;

    private String address;
    private String roadAddress;

    private Double latitude;
    private Double longitude;

    private String categoryName;

    private PreferenceType preferenceType;

    private LocalDate fixedDate;
    private LocalTime fixedTime;
}
