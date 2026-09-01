package com.example.hangat.course.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 저장 코스 이름 변경 (MY_001). 길이 제한은 명세서 21.0 courses.title VARCHAR(100). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseRenameRequest(
        @NotBlank @Size(max = 100) String title
) {
}
