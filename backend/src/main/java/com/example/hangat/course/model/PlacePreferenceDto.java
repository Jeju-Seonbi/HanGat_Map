package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class PlacePreferenceDto {

    @JsonProperty("place_id")
    private Long placeId;

    @JsonProperty("source_code")
    private String sourceCode;

    @JsonProperty("source_place_id")
    private String sourcePlaceId;

    @JsonProperty("place_name")
    private String placeName;

    private String address;

    @JsonProperty("road_address")
    private String roadAddress;

    private Double latitude;
    private Double longitude;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("preference_type")
    private PreferenceType preferenceType;

    @JsonProperty("fixed_date")
    private LocalDate fixedDate;

    @JsonProperty("fixed_time")
    private LocalTime fixedTime;
}
