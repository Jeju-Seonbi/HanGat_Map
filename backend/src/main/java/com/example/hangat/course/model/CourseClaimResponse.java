package com.example.hangat.course.model;

import com.example.hangat.course.model.enums.CourseStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseClaimResponse(
        Long id,
        CourseStatus status,
        String title,
        LocalDateTime savedAt
) {
}
