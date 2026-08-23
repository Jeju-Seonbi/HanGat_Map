package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AccommodationDto {

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

    private String phone;

    @JsonProperty("place_url")
    private String placeUrl;

    @JsonProperty("category_name")
    private String categoryName;

    private String region;

    @JsonProperty("image_url")
    private String imageUrl;
}
