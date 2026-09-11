package com.example.hangat.course.ai;

import com.example.hangat.course.CourseSchedulePolicy;
import com.example.hangat.course.model.Transport;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CourseReviewRegressionTest {
    private final CourseAiResultValidator validator = new CourseAiResultValidator();
    private final LocalDate start = LocalDate.of(2026, 9, 12);

    private CourseAiInputDto input(int days, List<String> selected,
            List<CourseAiInputDto.RequiredCandidateConstraintDto> required) {
        return new CourseAiInputDto("2.0",
                new CourseAiInputDto.TripConstraintDto(start, start.plusDays(days - 1), Transport.PUBLIC_TRANSIT),
                new CourseAiInputDto.SoftPreferencesDto(List.of("EAST"), selected),
                new CourseAiInputDto.HardConstraintsDto(required), null,
                List.of(candidate("a"), candidate("b"), candidate("c")), List.of(), List.of(), null);
    }

    private CourseAiInputDto.CandidateFactDto candidate(String id) {
        return new CourseAiInputDto.CandidateFactDto(id, id, "EAST", "TOURIST",
                List.of("PHOTO", "NATURE"), List.of(), null, null);
    }

    private CourseAiResultDto result(CourseAiResultDto.DayDto... days) {
        return new CourseAiResultDto("2.0", List.of(days));
    }

    private CourseAiResultDto.DayDto day(int offset, String id) {
        return new CourseAiResultDto.DayDto(start.plusDays(offset),
                List.of(new CourseAiResultDto.ItemDto(id, LocalTime.of(10, 0), "확인된 후보")));
    }

    @Test void rejectsEveryMissingDateAndEmptyDay() {
        var input = input(3, List.of(), List.of());
        for (var incomplete : List.of(result(day(1, "b"), day(2, "c")),
                result(day(0, "a"), day(2, "c")), result(day(0, "a"), day(1, "b")))) {
            assertThatThrownBy(() -> validator.validate(input, incomplete))
                    .isInstanceOfSatisfying(CourseAiValidationException.class,
                            e -> assertThat(e.getCode()).isEqualTo(CourseAiValidationCode.AI_RESULT_TRIP_DATE_MISSING));
        }
        assertThatThrownBy(() -> validator.validate(input,
                result(day(0, "a"), new CourseAiResultDto.DayDto(start.plusDays(1), List.of()), day(2, "c"))))
                .isInstanceOfSatisfying(CourseAiValidationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CourseAiValidationCode.AI_RESULT_DAY_ITEMS_EMPTY));
    }

    @Test void distributesThreeCandidatesAcrossThreeDaysAndPreservesFixedWant() {
        var input = input(3, List.of("PHOTO"), List.of(
                new CourseAiInputDto.RequiredCandidateConstraintDto("b", start.plusDays(1), LocalTime.of(14, 0))));
        var result = new DeterministicCourseFallback().generate(input);
        assertThatCode(() -> validator.validate(input, result)).doesNotThrowAnyException();
        assertThat(result.days()).hasSize(3).allSatisfy(day -> assertThat(day.items()).hasSize(1));
        assertThat(result.days().get(1).items().get(0).candidateId()).isEqualTo("b");
        assertThat(result.days().get(1).items().get(0).startTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test void missingDateUsesCorrectionThenValidatedFallback() {
        var input = input(3, List.of(), List.of());
        AtomicInteger corrections = new AtomicInteger();
        CourseAiProvider provider = new CourseAiProvider() {
            public CourseAiResultDto generate(CourseAiInputDto ignored) { return result(day(0, "a")); }
            public CourseAiResultDto generateCorrection(CourseAiInputDto ignored, CourseAiResultDto previous,
                    CourseAiValidationCode code, String message) {
                assertThat(code).isEqualTo(CourseAiValidationCode.AI_RESULT_TRIP_DATE_MISSING);
                corrections.incrementAndGet();
                return previous;
            }
        };
        var result = new CourseAiGenerationService(provider, validator).generate(input);
        assertThat(corrections).hasValue(1);
        assertThat(result.days()).hasSize(3).allSatisfy(day -> assertThat(day.items()).hasSize(1));
    }

    @Test void selectedStyleIntersectionControlsValidationAndSharedDwellCalculation() {
        var photo = input(1, List.of("PHOTO"), List.of());
        var late = result(new CourseAiResultDto.DayDto(start, List.of(
                new CourseAiResultDto.ItemDto("a", LocalTime.of(20, 45), "사진 방문"))));
        assertThatCode(() -> validator.validate(photo, late)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(input(1, List.of("PHOTO", "NATURE"), List.of()), late))
                .isInstanceOf(CourseAiValidationException.class);
        assertThat(CourseSchedulePolicy.dwellMinutes(List.of("PHOTO"), List.of("PHOTO", "NATURE"), "TOURIST")).isEqualTo(75);
        assertThat(CourseSchedulePolicy.dwellMinutes(List.of("PHOTO", "NATURE"), List.of("NATURE", "PHOTO"), "TOURIST")).isEqualTo(120);
        assertThat(CourseSchedulePolicy.dwellMinutes(List.of("PHOTO"), List.of("NATURE"), "TOURIST")).isEqualTo(90);
        assertThat(CourseSchedulePolicy.dwellMinutes(List.of(), List.of("NATURE"), "CAFE")).isEqualTo(60);
    }
}
