package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiInputDto.GenerationMetadataDto;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.model.Transport;
import com.example.hangat.course.travel.CourseTravelLegDto;
import com.example.hangat.course.travel.DistanceCalculationMethod;
import com.example.hangat.course.weather.CourseWeatherDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseAiInputAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final CourseAiInputAssembler assembler = new CourseAiInputAssembler();

    @Test
    void assemblesSeparatedConditionsFactsAndMissingRouteData() throws Exception {
        CourseRequestDto request = request();
        CourseCandidateDto wantCandidate = new CourseCandidateDto(
                tourPlace("125266", "비자림", "제주특별자치도 제주시 구좌읍 비자숲길 55"),
                List.of(congestion("20260827", "72.50")),
                PreferenceType.WANT,
                List.of("NATURE"));
        CourseCandidateDto ordinaryCandidate = new CourseCandidateDto(
                tourPlace("126450", "성산일출봉", "제주특별자치도 서귀포시 성산읍 일출로 284-12"),
                List.of(),
                null,
                List.of("NATURE", "ACTIVITY"));
        CourseCandidateDto avoidCandidate = new CourseCandidateDto(
                tourPlace("999999", "제외 장소", "제주특별자치도 제주시"),
                List.of(),
                PreferenceType.AVOID,
                List.of());
        CourseWeatherDto weather = new CourseWeatherDto(
                LocalDate.of(2026, 8, 27), LocalTime.of(12, 0),
                new BigDecimal("28.5"), 30, "0", "1",
                new BigDecimal("3.2"), 70);
        CourseTravelLegDto travel = new CourseTravelLegDto(
                "125266", "비자림", "126450", "성산일출봉",
                new BigDecimal("12.345"), DistanceCalculationMethod.HAVERSINE,
                null, null, Transport.RENTAL_CAR, null, null);

        CourseAiInputDto result = assembler.assemble(
                request,
                List.of(wantCandidate, ordinaryCandidate, avoidCandidate),
                Map.of("125266", List.of(weather)),
                List.of(travel),
                new GenerationMetadataDto(GenerationReason.INITIAL, "course-ai-v1", "request-1"));

        assertThat(result.tripCondition().startDate()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(result.tripCondition().people()).isEqualTo(2);
        assertThat(result.tripCondition().budgetTotal()).isEqualTo(500000);
        assertThat(result.tripCondition().transport()).isEqualTo(Transport.RENTAL_CAR);
        assertThat(result.userPreferences().selectedRegions()).extracting("code").containsExactly("EAST");
        assertThat(result.userPreferences().selectedStyles()).extracting("code").containsExactly("NATURE");
        assertThat(result.userPreferences().selectedStyles().get(0).weight())
                .isEqualByComparingTo("0.8");

        assertThat(result.userPreferences().requiredPlaces()).hasSize(1);
        assertThat(result.userPreferences().requiredPlaces().get(0).preferenceType())
                .isEqualTo(PreferenceType.WANT);
        assertThat(result.userPreferences().requiredPlaces().get(0).fixedDate())
                .isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(result.userPreferences().requiredPlaces().get(0).fixedTime())
                .isEqualTo(LocalTime.of(10, 30));
        assertThat(result.userPreferences().forbiddenPlaces()).hasSize(1);
        assertThat(result.userPreferences().forbiddenPlaces().get(0).preferenceType())
                .isEqualTo(PreferenceType.AVOID);

        assertThat(result.candidates()).hasSize(2);
        CourseAiInputDto.CandidateFactDto first = result.candidates().get(0);
        assertThat(first.identity().candidateId()).isEqualTo("125266");
        assertThat(first.identity().placeId()).isNull();
        assertThat(first.identity().sourceCode()).isNull();
        assertThat(first.identity().sourcePlaceId()).isNull();
        assertThat(first.preferenceType()).isEqualTo(PreferenceType.WANT);
        assertThat(first.regionCode()).isEqualTo("EAST");
        assertThat(first.confirmedStyleHints()).containsExactly("NATURE");
        assertThat(first.congestion().get(0).rate()).isEqualByComparingTo("72.50");
        assertThat(first.congestion().get(0).level().name()).isEqualTo("CROWDED");
        assertThat(first.weather()).containsExactly(weather);

        CourseAiInputDto.CandidateFactDto second = result.candidates().get(1);
        assertThat(second.confirmedStyleHints()).containsExactly("NATURE", "ACTIVITY");
        assertThat(second.congestion()).isEmpty();
        assertThat(second.weather()).isNull();

        assertThat(result.travelFacts()).hasSize(1);
        assertThat(result.travelFacts().get(0).straightDistanceKm())
                .isEqualByComparingTo("12.345");
        assertThat(result.travelFacts().get(0).routeDistanceKm()).isNull();
        assertThat(result.travelFacts().get(0).durationMinutes()).isNull();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
        assertThat(json.path("tripCondition").path("startDate").asText()).isEqualTo("2026-08-27");
        assertThat(json.path("userPreferences").path("selectedStyles").get(0).path("code").asText())
                .isEqualTo("NATURE");
        assertThat(json.path("candidates").get(0).path("confirmedStyleHints").get(0).asText())
                .isEqualTo("NATURE");
        assertThat(json.path("candidates").get(0).path("congestion").get(0).path("rate").decimalValue())
                .isEqualByComparingTo("72.50");
        assertThat(json.path("candidates").get(1).path("weather").isNull()).isTrue();
        assertThat(json.path("travelFacts").get(0).path("routeDistanceKm").isNull()).isTrue();
        assertThat(json.path("travelFacts").get(0).path("durationMinutes").isNull()).isTrue();
    }

    @Test
    void keepsInternalAndExternalPreferenceIdsSeparate() throws Exception {
        CourseAiInputDto result = assembler.assemble(
                request(), List.of(), Map.of(), List.of(),
                new GenerationMetadataDto(GenerationReason.INITIAL, null, null));

        CourseAiInputDto.PlaceIdentityDto wantIdentity =
                result.userPreferences().requiredPlaces().get(0).identity();
        CourseAiInputDto.PlaceIdentityDto avoidIdentity =
                result.userPreferences().forbiddenPlaces().get(0).identity();

        assertThat(wantIdentity.placeId()).isEqualTo(101L);
        assertThat(wantIdentity.sourceCode()).isNull();
        assertThat(wantIdentity.sourcePlaceId()).isNull();
        assertThat(avoidIdentity.placeId()).isNull();
        assertThat(avoidIdentity.sourceCode()).isEqualTo("KAKAO_LOCAL");
        assertThat(avoidIdentity.sourcePlaceId()).isEqualTo("external-9");
    }

    @Test
    void representsUnknownCongestionAndUnknownRegionWithoutInventingFacts() throws Exception {
        CourseCandidateDto candidate = new CourseCandidateDto(
                tourPlace("unknown-1", "미분류 장소", "주소 미상"),
                List.of(congestion("bad-date", "not-a-number")),
                PreferenceType.WANT,
                List.of());

        CourseAiInputDto result = assembler.assemble(
                request(), List.of(candidate), Map.of(), List.of(),
                new GenerationMetadataDto(GenerationReason.INITIAL, null, null));

        assertThat(result.candidates().get(0).regionCode()).isEqualTo("UNKNOWN");
        assertThat(result.candidates().get(0).congestion().get(0).date()).isNull();
        assertThat(result.candidates().get(0).congestion().get(0).rate()).isNull();
        assertThat(result.candidates().get(0).congestion().get(0).level()).isNull();
    }

    @Test
    void rejectsTravelFactWithUnknownFromCandidate() throws Exception {
        assertUnknownTravelReference("missing", "125266");
    }

    @Test
    void rejectsTravelFactWithUnknownToCandidate() throws Exception {
        assertUnknownTravelReference("125266", "missing");
    }

    @Test
    void rejectsTravelFactWithMissingCandidateId() throws Exception {
        assertUnknownTravelReference(null, "125266");
    }

    private void assertUnknownTravelReference(String fromCandidateId, String toCandidateId)
            throws Exception {
        CourseCandidateDto candidate = new CourseCandidateDto(
                tourPlace("125266", "비자림", "제주특별자치도 제주시 구좌읍 비자숲길 55"),
                List.of(), null, List.of("NATURE"));
        CourseTravelLegDto travel = new CourseTravelLegDto(
                fromCandidateId, "출발지", toCandidateId, "도착지",
                new BigDecimal("1.0"), DistanceCalculationMethod.HAVERSINE,
                null, null, Transport.RENTAL_CAR, null, null);

        assertThatThrownBy(() -> assembler.assemble(
                request(), List.of(candidate), Map.of(), List.of(travel),
                new GenerationMetadataDto(GenerationReason.INITIAL, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CourseRequestDto request() throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date": "2026-08-27",
                  "end_date": "2026-08-29",
                  "people": 2,
                  "budget_total": 500000,
                  "transport": "RENTAL_CAR",
                  "course_regions": [
                    {"region_id": 2, "code": "EAST", "name": "동부"}
                  ],
                  "course_styles": [
                    {"tag_id": 1, "code": "NATURE", "name": "자연", "weight": 0.8}
                  ],
                  "course_place_preferences": [
                    {
                      "place_id": 101,
                      "place_name": "비자림",
                      "preference_type": "WANT",
                      "fixed_date": "2026-08-27",
                      "fixed_time": "10:30"
                    },
                    {
                      "source_code": "KAKAO_LOCAL",
                      "source_place_id": "external-9",
                      "place_name": "제외 장소",
                      "preference_type": "AVOID"
                    }
                  ],
                  "accommodation": {
                    "source_code": "KAKAO_LOCAL",
                    "source_place_id": "lodging-1",
                    "place_name": "숙소",
                    "latitude": 33.4,
                    "longitude": 126.5,
                    "region": "EAST"
                  }
                }
                """, CourseRequestDto.class);
    }

    private TourPlaceDto tourPlace(String contentId, String title, String address) throws Exception {
        return objectMapper.readValue("""
                {
                  "contentid": "%s",
                  "title": "%s",
                  "addr1": "%s",
                  "mapy": 33.485,
                  "mapx": 126.811,
                  "cat1": "A01",
                  "cat2": "A0101",
                  "cat3": "A01010100"
                }
                """.formatted(contentId, title, address), TourPlaceDto.class);
    }

    private CongestionDto congestion(String date, String rate) throws Exception {
        return objectMapper.readValue("""
                {"baseYmd": "%s", "cnctrRate": "%s"}
                """.formatted(date, rate), CongestionDto.class);
    }
}
