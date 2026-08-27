package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiResultDto;
import com.example.hangat.course.model.CongestionLevel;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseResponseDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.model.Transport;
import com.example.hangat.course.weather.CourseWeatherDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseResponseAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CourseResponseAssembler assembler = new CourseResponseAssembler();

    @Test
    void restoresBackendFactsAndPreservesAiDayAndItemOrder() throws Exception {
        CourseAiInputDto input = input(List.of(
                fact("first", "첫 장소", PreferenceType.WANT, List.of(
                        new CourseAiInputDto.CongestionFactDto(
                                LocalDate.of(2026, 8, 28),
                                new BigDecimal("72.50"),
                                CongestionLevel.CROWDED)),
                        List.of(new CourseWeatherDto(
                                LocalDate.of(2026, 8, 28), LocalTime.of(9, 0),
                                new BigDecimal("27.5"), 20, "0", "1",
                                new BigDecimal("2.1"), 65))),
                fact("second", "둘째 장소", null, List.of(), null)));
        CourseAiResultDto result = new CourseAiResultDto("1.0", List.of(
                day("2026-08-29", item("second", "11:00", "두 번째 날을 먼저 반환")),
                day("2026-08-28", item("second", "10:30", "일반 추천"),
                        item("first", "09:00", "한글 고정 일정 추천"))));

        CourseResponseDto response = assembler.assemble(
                input,
                result,
                List.of(original("first", "첫 장소", "first.jpg"),
                        original("second", "둘째 장소", "second.jpg")));

        assertThat(response.days()).extracting(CourseResponseDto.DayDto::visitDate)
                .containsExactly(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 28));
        assertThat(response.days().get(1).items())
                .extracting(CourseResponseDto.ItemDto::candidateId)
                .containsExactly("second", "first");

        CourseResponseDto.ItemDto fixed = response.days().get(1).items().get(1);
        assertThat(fixed.placeName()).isEqualTo("첫 장소");
        assertThat(fixed.imageUrl()).isEqualTo("first.jpg");
        assertThat(fixed.recommendationReason()).isEqualTo("한글 고정 일정 추천");
        assertThat(fixed.itemSource()).isEqualTo(CourseResponseDto.ItemSource.USER_FIXED);
        assertThat(fixed.congestion()).singleElement().satisfies(congestion -> {
            assertThat(congestion.rate()).isEqualByComparingTo("72.50");
            assertThat(congestion.level()).isEqualTo(CongestionLevel.CROWDED);
        });
        assertThat(fixed.weather()).singleElement().satisfies(weather ->
                assertThat(weather.temperature()).isEqualByComparingTo("27.5"));

        CourseResponseDto.ItemDto missingFacts = response.days().get(0).items().get(0);
        assertThat(missingFacts.congestion()).isEmpty();
        assertThat(missingFacts.weather()).isNull();
    }

    @Test
    void rejectsCandidateIdMissingFromOriginalCandidateCollection() throws Exception {
        CourseAiInputDto input = input(List.of(fact("known", "장소", null, List.of(), null)));
        CourseAiResultDto result = new CourseAiResultDto(
                "1.0", List.of(day("2026-08-28", item("unknown", "09:00", "추천"))));

        assertThatThrownBy(() -> assembler.assemble(
                input, result, List.of(original("known", "장소", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    private CourseAiInputDto input(List<CourseAiInputDto.CandidateFactDto> candidates) {
        CourseAiInputDto.PlaceConstraintDto fixedWant = new CourseAiInputDto.PlaceConstraintDto(
                new CourseAiInputDto.PlaceIdentityDto(null, null, null, null),
                "첫 장소", "주소", null, null, null,
                PreferenceType.WANT, LocalDate.of(2026, 8, 28), LocalTime.of(9, 0));
        return new CourseAiInputDto(
                "1.0",
                new CourseAiInputDto.TripConditionDto(
                        LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 29),
                        2, 500000, Transport.RENTAL_CAR),
                new CourseAiInputDto.UserPreferencesDto(
                        List.of(), List.of(), List.of(fixedWant), List.of(), null),
                candidates, List.of(), null);
    }

    private CourseAiInputDto.CandidateFactDto fact(
            String id,
            String name,
            PreferenceType preferenceType,
            List<CourseAiInputDto.CongestionFactDto> congestion,
            List<CourseWeatherDto> weather
    ) {
        return new CourseAiInputDto.CandidateFactDto(
                new CourseAiInputDto.PlaceIdentityDto(id, null, null, null),
                name, "제주 주소", 33.4, 126.8,
                new CourseAiInputDto.TourCategoryDto("A01", "A0101", "A01010100"),
                "EAST", preferenceType, List.of("NATURE"), congestion, weather);
    }

    private CourseCandidateDto original(
            String id,
            String name,
            String imageUrl
    ) throws Exception {
        TourPlaceDto place = objectMapper.readValue("""
                {"contentid":"%s","title":"%s","addr1":"제주 주소",
                 "mapy":33.4,"mapx":126.8,"cat1":"A01","firstimage":%s}
                """.formatted(id, name,
                        imageUrl == null ? "null" : "\"" + imageUrl + "\""), TourPlaceDto.class);
        return new CourseCandidateDto(place, List.of(), null, List.of("NATURE"));
    }

    private CourseAiResultDto.DayDto day(String date, CourseAiResultDto.ItemDto... items) {
        return new CourseAiResultDto.DayDto(LocalDate.parse(date), List.of(items));
    }

    private CourseAiResultDto.ItemDto item(
            String candidateId,
            String startTime,
            String reason
    ) {
        return new CourseAiResultDto.ItemDto(
                candidateId, LocalTime.parse(startTime), reason);
    }
}
