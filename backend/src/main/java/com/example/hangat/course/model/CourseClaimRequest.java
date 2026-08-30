package com.example.hangat.course.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseClaimRequest(
        @NotBlank String claimToken,
        @NotBlank @Size(max = 100) String title
) {
}
