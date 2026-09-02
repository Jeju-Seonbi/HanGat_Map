package com.example.hangat.course.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

/**
 * 스왑 요청 - 대안 시트에서 고른 장소 하나.
 * placeId는 {@code GET /places/{id}/alternatives} 응답의 {@code place_id}를 그대로 넘긴다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseSwapRequest(
        @NotNull Long placeId
) {
}
