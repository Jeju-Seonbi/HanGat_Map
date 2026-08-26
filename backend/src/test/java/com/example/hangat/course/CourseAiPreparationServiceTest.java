package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.travel.CourseTravelService;
import com.example.hangat.course.travel.StraightLineDistanceCalculator;
import com.example.hangat.course.weather.CourseWeatherDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAiPreparationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void preparesOperationalAiInputWithoutInventingUnavailableFacts() throws Exception {
        List<TourPlaceDto> tourPlaces = List.of(
                place("east-want", "성산일출봉", "제주특별자치도 서귀포시 성산읍", 33.458, 126.942, "A01"),
                place("east-avoid", "비자림", "제주특별자치도 제주시 구좌읍", 33.491, 126.811, "A01"),
                place("east-normal", "만장굴", "제주특별자치도 제주시 구좌읍", 33.529, 126.771, "A01")
        );
        List<CongestionDto> wantCongestion = List.of(congestion("20260828", "82.00"));
        TourApiService tourApiService = new TourApiService() {
            @Override
            public List<TourPlaceDto> getTourPlaces() {
                return tourPlaces;
            }
        };
        CongestionApiService congestionApiService = new CongestionApiService() {
            @Override
            public List<CongestionDto> getCongestionData(String signguCd, String name) {
                return "성산일출봉".equals(name)
                        ? wantCongestion
                        : List.of();
            }
        };
        CourseAiPreparationService preparationService = new CourseAiPreparationService(
                new CourseAiInputAssembler(),
                new CourseTravelService(new StraightLineDistanceCalculator()),
                Optional.empty());
        CourseService courseService = new CourseService(
                tourApiService, congestionApiService, preparationService);

        CourseAiInputDto result = courseService.prepareAiInput(request());

        assertThat(result.candidates()).extracting("name")
                .containsExactly("성산일출봉", "만장굴");
        assertThat(result.userPreferences().requiredPlaces()).hasSize(1);
        assertThat(result.userPreferences().requiredPlaces().get(0).fixedDate().toString())
                .isEqualTo("2026-08-28");
        assertThat(result.userPreferences().forbiddenPlaces()).hasSize(1);
        assertThat(result.candidates().get(0).confirmedStyleHints()).containsExactly("NATURE");
        assertThat(result.candidates().get(0).congestion().get(0).rate())
                .isEqualByComparingTo("82.00");
        assertThat(result.candidates()).allSatisfy(candidate -> assertThat(candidate.weather()).isNull());

        assertThat(result.travelFacts()).hasSize(1);
        CourseAiInputDto.TravelFactDto travel = result.travelFacts().get(0);
        assertThat(travel.fromCandidateId()).isEqualTo("east-want");
        assertThat(travel.toCandidateId()).isEqualTo("east-normal");
        assertThat(travel.straightDistanceKm()).isPositive();
        assertThat(travel.routeDistanceKm()).isNull();
        assertThat(travel.durationMinutes()).isNull();
        assertThat(result.candidates()).extracting(candidate -> candidate.identity().candidateId())
                .contains(travel.fromCandidateId(), travel.toCandidateId());

        String json = objectMapper.writeValueAsString(result);
        assertThat(json).contains("\"generationReason\":\"INITIAL\"");
        assertThat(json).contains("\"algorithmVersion\":null");
        assertThat(json).contains("\"requestReference\":null");
        assertThat(json.toLowerCase())
                .doesNotContain("servicekey", "authorization", "credential", "gemini");
    }

    @Test
    void includesWeatherOnlyWhenVerifiedFactsProviderSuppliesIt() throws Exception {
        TourPlaceDto place = place(
                "east-normal", "만장굴", "제주특별자치도 제주시 구좌읍",
                33.529, 126.771, "A01");
        CourseCandidateDto candidate = new CourseCandidateDto(
                place, List.of(), null, List.of("NATURE"));
        CourseWeatherDto weather = new CourseWeatherDto(
                LocalDate.of(2026, 8, 28), LocalTime.of(12, 0),
                new BigDecimal("28.5"), 20, "0", "1",
                new BigDecimal("3.2"), 70);
        CourseAiPreparationService preparationService = new CourseAiPreparationService(
                new CourseAiInputAssembler(),
                new CourseTravelService(new StraightLineDistanceCalculator()),
                Optional.of((request, candidates) -> Map.of("east-normal", List.of(weather))));

        CourseAiInputDto result = preparationService.prepare(
                request(), List.of(candidate));

        assertThat(result.candidates().get(0).weather()).containsExactly(weather);
    }

    private CourseRequestDto request() throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date": "2026-08-27",
                  "end_date": "2026-08-29",
                  "people": 2,
                  "budget_total": 500000,
                  "transport": "RENTAL_CAR",
                  "course_regions": [{"region_id": 2, "code": "EAST", "name": "동부"}],
                  "course_styles": [{"tag_id": 1, "code": "NATURE", "name": "자연", "weight": 1}],
                  "course_place_preferences": [
                    {"place_name": "성산일출봉", "preference_type": "WANT", "fixed_date": "2026-08-28", "fixed_time": "09:00"},
                    {"place_name": "비자림", "preference_type": "AVOID"}
                  ]
                }
                """, CourseRequestDto.class);
    }

    private TourPlaceDto place(
            String id, String title, String address,
            double latitude, double longitude, String category
    ) throws Exception {
        return objectMapper.readValue("""
                {"contentid":"%s","title":"%s","addr1":"%s",
                 "mapy":%s,"mapx":%s,"cat1":"%s"}
                """.formatted(id, title, address, latitude, longitude, category),
                TourPlaceDto.class);
    }

    private CongestionDto congestion(String date, String rate) throws Exception {
        return objectMapper.readValue(
                "{\"baseYmd\":\"%s\",\"cnctrRate\":\"%s\"}"
                        .formatted(date, rate),
                CongestionDto.class);
    }
}
