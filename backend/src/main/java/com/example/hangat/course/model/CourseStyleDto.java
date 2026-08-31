package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class CourseStyleDto {

    @JsonProperty("tag_id")
    private Long tagId;

    private String code;
    private String name;
    private BigDecimal weight;
}
