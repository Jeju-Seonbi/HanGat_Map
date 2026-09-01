package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CourseRegionDto {

    @JsonProperty("region_id")
    private Long regionId;

    private String code;
    private String name;
}
