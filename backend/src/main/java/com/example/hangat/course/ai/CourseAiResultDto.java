package com.example.hangat.course.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseAiResultDto(
        String contractVersion,
        List<DayDto> days
) {
    public CourseAiResultDto {
        days = days == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(days));
    }

    public record DayDto(LocalDate date, List<ItemDto> items) {
        public DayDto {
            items = items == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public record ItemDto(
            String candidateId,
            LocalTime startTime,
            String recommendationReason
    ) {
    }
}
