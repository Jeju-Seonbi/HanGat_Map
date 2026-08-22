package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TourPlaceDto {

    @JsonProperty("contentid")
    private String contentId;

    private String title;

    @JsonProperty("addr1")
    private String address;

    @JsonProperty("mapy")
    private Double latitude;

    @JsonProperty("mapx")
    private Double longitude;

    @JsonProperty("cat1")
    private String category;

    @JsonProperty("firstimage")
    private String imageUrl;
}
