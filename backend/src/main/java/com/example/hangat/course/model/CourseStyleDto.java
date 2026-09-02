package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class CourseStyleDto {

    @JsonProperty("tag_id")
    private Long tagId;

    @NotBlank(message = "여행 스타일 코드는 필수입니다.")
    @Pattern(
            regexp = "NATURE|LOCAL|CAFE|ACTIVITY|WITH_KIDS|PHOTO",
            message = "지원하지 않는 여행 스타일입니다.")
    private String code;
    private String name;
    private BigDecimal weight;
}
