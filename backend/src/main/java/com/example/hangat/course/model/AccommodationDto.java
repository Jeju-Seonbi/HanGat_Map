package com.example.hangat.course.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AccommodationDto {

    private Long placeId;

    private String sourceCode;
    private String sourcePlaceId;

    private String placeName;

    private String address;
    private String roadAddress;

    private Double latitude;
    private Double longitude;

    private String phone;
    private String placeUrl;
    private String categoryName;

    private String region;
    private String imageUrl;
}
