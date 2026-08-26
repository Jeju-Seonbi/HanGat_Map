package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceIdentityDto;
import com.example.hangat.course.ai.CourseAiInputDto.TripConditionDto;
import com.example.hangat.course.ai.CourseAiInputDto.UserPreferencesDto;
import com.example.hangat.course.ai.CourseAiResultDto.DayDto;
import com.example.hangat.course.ai.CourseAiResultDto.ItemDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.Transport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAiGenerationServiceTest {

    @Test
    void invokesProviderThenValidator() {
        AtomicBoolean providerCalled = new AtomicBoolean();
        CourseAiInputDto input = input();
        CourseAiResultDto result = new CourseAiResultDto("1.0", List.of(
                new DayDto(LocalDate.parse("2026-08-28"), List.of(
                        new ItemDto("want-1", LocalTime.parse("09:00"), "입력 근거")))));
        CourseAiGenerationService service = new CourseAiGenerationService(
                actualInput -> {
                    assertThat(actualInput).isSameAs(input);
                    providerCalled.set(true);
                    return result;
                },
                new CourseAiResultValidator());

        assertThat(service.generate(input)).isSameAs(result);
        assertThat(providerCalled).isTrue();
    }

    private CourseAiInputDto input() {
        CandidateFactDto want = new CandidateFactDto(
                new PlaceIdentityDto("want-1", null, null, null),
                "성산일출봉", "주소", 33.4, 126.9, null, "EAST",
                PreferenceType.WANT, List.of(), List.of(), null);
        return new CourseAiInputDto(
                "1.0",
                new TripConditionDto(
                        LocalDate.parse("2026-08-27"), LocalDate.parse("2026-08-29"),
                        2, 500000, Transport.RENTAL_CAR),
                new UserPreferencesDto(List.of(), List.of(), List.of(), List.of(), null),
                List.of(want), List.of(), null);
    }
}
