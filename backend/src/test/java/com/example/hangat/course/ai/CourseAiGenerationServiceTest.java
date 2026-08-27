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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void correctsOneValidationFailureAndReturnsValidResult() {
        CourseAiInputDto input = input();
        CourseAiResultDto duplicate = duplicateResult();
        CourseAiResultDto corrected = validResult();
        AtomicInteger calls = new AtomicInteger();
        CourseAiProvider provider = new CourseAiProvider() {
            @Override
            public CourseAiResultDto generate(CourseAiInputDto actualInput) {
                calls.incrementAndGet();
                return duplicate;
            }

            @Override
            public CourseAiResultDto generateCorrection(
                    CourseAiInputDto actualInput,
                    String validationFailureReason
            ) {
                calls.incrementAndGet();
                assertThat(actualInput).isSameAs(input);
                assertThat(validationFailureReason).contains("중복 배치");
                return corrected;
            }
        };
        CourseAiGenerationService service = new CourseAiGenerationService(
                provider, new CourseAiResultValidator());

        assertThat(service.generate(input)).isSameAs(corrected);
        assertThat(calls).hasValue(2);
    }

    @Test
    void failsAfterOneCorrectionWhenCorrectedResultIsStillInvalid() {
        CourseAiInputDto input = input();
        AtomicInteger calls = new AtomicInteger();
        CourseAiProvider provider = new CourseAiProvider() {
            @Override
            public CourseAiResultDto generate(CourseAiInputDto actualInput) {
                calls.incrementAndGet();
                return duplicateResult();
            }

            @Override
            public CourseAiResultDto generateCorrection(
                    CourseAiInputDto actualInput,
                    String validationFailureReason
            ) {
                calls.incrementAndGet();
                return duplicateResult();
            }
        };
        CourseAiGenerationService service = new CourseAiGenerationService(
                provider, new CourseAiResultValidator());

        assertThatThrownBy(() -> service.generate(input))
                .isInstanceOfSatisfying(CourseAiException.class, exception ->
                        assertThat(exception.getFailureType())
                                .isEqualTo(CourseAiFailureType.VALIDATION_ERROR));
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryProviderFailure() {
        AtomicInteger calls = new AtomicInteger();
        CourseAiGenerationService service = new CourseAiGenerationService(
                actualInput -> {
                    calls.incrementAndGet();
                    throw new CourseAiException(
                            CourseAiFailureType.PROVIDER_ERROR,
                            "Gemini provider failure");
                },
                new CourseAiResultValidator());

        assertThatThrownBy(() -> service.generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception ->
                        assertThat(exception.getFailureType())
                                .isEqualTo(CourseAiFailureType.PROVIDER_ERROR));
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryRateLimitFailure() {
        AtomicInteger calls = new AtomicInteger();
        CourseAiGenerationService service = new CourseAiGenerationService(
                actualInput -> {
                    calls.incrementAndGet();
                    throw new CourseAiException(
                            CourseAiFailureType.RATE_LIMIT,
                            "Gemini rate limit");
                },
                new CourseAiResultValidator());

        assertThatThrownBy(() -> service.generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception ->
                        assertThat(exception.getFailureType())
                                .isEqualTo(CourseAiFailureType.RATE_LIMIT));
        assertThat(calls).hasValue(1);
    }

    private CourseAiResultDto validResult() {
        return new CourseAiResultDto("1.0", List.of(
                new DayDto(LocalDate.parse("2026-08-28"), List.of(
                        new ItemDto("want-1", LocalTime.parse("09:00"), "입력 근거")))));
    }

    private CourseAiResultDto duplicateResult() {
        return new CourseAiResultDto("1.0", List.of(
                new DayDto(LocalDate.parse("2026-08-28"), List.of(
                        new ItemDto("want-1", LocalTime.parse("09:00"), "입력 근거"))),
                new DayDto(LocalDate.parse("2026-08-29"), List.of(
                        new ItemDto("want-1", LocalTime.parse("11:00"), "중복 근거")))));
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
