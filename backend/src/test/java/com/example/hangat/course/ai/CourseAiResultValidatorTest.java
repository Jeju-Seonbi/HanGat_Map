package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceConstraintDto;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseAiResultValidatorTest {

    private final CourseAiResultValidator validator = new CourseAiResultValidator();

    @Test
    void acceptsValidResultAndPreservesFixedWant() {
        assertThatCode(() -> validator.validate(input(), validResult()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownAndDuplicateCandidates() {
        assertInvalid(result(day("2026-08-28", item("unknown", "09:00"))));
        assertInvalid(result(day("2026-08-28",
                item("want-1", "09:00"), item("want-1", "11:00"))));
    }

    @Test
    void rejectsSameCandidateAcrossDifferentDatesAndTimes() {
        assertInvalid(result(
                day("2026-08-28", item("want-1", "09:00")),
                day("2026-08-29", item("want-1", "11:00"))));
    }

    @Test
    void rejectsMissingWantAndForbiddenPlace() {
        assertInvalid(result(day("2026-08-28", item("normal-1", "11:00"))));
        CourseAiInputDto forbiddenInput = withForbiddenCandidate();
        assertThatThrownBy(() -> validator.validate(
                forbiddenInput,
                result(day("2026-08-28",
                        item("want-1", "09:00"), item("normal-1", "11:00")))))
                .isInstanceOfSatisfying(CourseAiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getFailureType())
                                .isEqualTo(CourseAiFailureType.VALIDATION_ERROR));
    }

    @Test
    void rejectsChangedFixedDateOrTimeAndOutOfRangeDate() {
        assertInvalid(result(day("2026-08-29", item("want-1", "09:00"))));
        assertInvalid(result(day("2026-08-28", item("want-1", "10:00"))));
        assertInvalid(result(day("2026-08-30", item("want-1", "09:00"))));
    }

    @Test
    void rejectsDuplicateDay() {
        assertInvalid(result(
                day("2026-08-28", item("want-1", "09:00")),
                day("2026-08-28", item("normal-1", "11:00"))));
    }

    private void assertInvalid(CourseAiResultDto result) {
        assertThatThrownBy(() -> validator.validate(input(), result))
                .isInstanceOfSatisfying(CourseAiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getFailureType())
                                .isEqualTo(CourseAiFailureType.VALIDATION_ERROR));
    }

    private CourseAiInputDto input() {
        PlaceConstraintDto required = new PlaceConstraintDto(
                new PlaceIdentityDto(null, 1L, null, null),
                "성산일출봉", "주소", null, null, null,
                PreferenceType.WANT, LocalDate.parse("2026-08-28"), LocalTime.parse("09:00"));
        return new CourseAiInputDto(
                "1.0",
                new TripConditionDto(
                        LocalDate.parse("2026-08-27"), LocalDate.parse("2026-08-29"),
                        2, 500000, Transport.RENTAL_CAR),
                new UserPreferencesDto(List.of(), List.of(), List.of(required), List.of(), null),
                List.of(candidate("want-1", "성산일출봉", PreferenceType.WANT),
                        candidate("normal-1", "만장굴", null)),
                List.of(),
                null
        );
    }

    private CourseAiInputDto withForbiddenCandidate() {
        CourseAiInputDto base = input();
        PlaceConstraintDto forbidden = new PlaceConstraintDto(
                new PlaceIdentityDto(null, 2L, null, null),
                "만장굴", "주소", null, null, null,
                PreferenceType.AVOID, null, null);
        return new CourseAiInputDto(
                base.contractVersion(), base.tripCondition(),
                new UserPreferencesDto(List.of(), List.of(),
                        base.userPreferences().requiredPlaces(), List.of(forbidden), null),
                base.candidates(), base.travelFacts(), base.generationMetadata());
    }

    private CandidateFactDto candidate(String id, String name, PreferenceType preferenceType) {
        return new CandidateFactDto(
                new PlaceIdentityDto(id, null, null, null), name, "주소",
                33.4, 126.8, null, "EAST", preferenceType,
                List.of(), List.of(), null);
    }

    private CourseAiResultDto validResult() {
        return result(day("2026-08-28", item("want-1", "09:00")));
    }

    private CourseAiResultDto result(DayDto... days) {
        return new CourseAiResultDto("1.0", List.of(days));
    }

    private DayDto day(String date, ItemDto... items) {
        return new DayDto(LocalDate.parse(date), List.of(items));
    }

    private ItemDto item(String candidateId, String time) {
        return new ItemDto(candidateId, LocalTime.parse(time), "입력 사실 기반 추천");
    }
}
