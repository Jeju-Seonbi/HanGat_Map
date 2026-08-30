package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiGenerationService;
import com.example.hangat.course.ai.CourseAiResultDto;
import com.example.hangat.course.ai.CourseAiResultValidator;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.travel.CourseTravelService;
import com.example.hangat.course.travel.StraightLineDistanceCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class CourseServiceAiGenerationFlowTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void createCoursePreparesInputThenInvokesProviderAndValidator() throws Exception {
        AtomicBoolean providerCalled = new AtomicBoolean();
        CourseAiGenerationService generationService = new CourseAiGenerationService(
                input -> {
                    assertThat(input.candidates()).hasSize(1);
                    assertThat(input.candidates().get(0).identity().candidateId()).isEqualTo("candidate-1");
                    providerCalled.set(true);
                    return new CourseAiResultDto(input.contractVersion(), List.of(
                            new CourseAiResultDto.DayDto(
                                    LocalDate.of(2026, 8, 27),
                                    List.of(new CourseAiResultDto.ItemDto(
                                            "candidate-1",
                                            LocalTime.of(9, 0),
                                            "한글 추천 이유")))));
                },
                new CourseAiResultValidator());
        CoursePersistenceService persistenceService = mock(CoursePersistenceService.class);
        CourseService service = new CourseService(
                new StubTourApiService(),
                new StubCongestionApiService(),
                new CourseCandidateShortlistService(),
                new CourseAiPreparationService(
                        new CourseAiInputAssembler(),
                        new CourseTravelService(new StraightLineDistanceCalculator()),
                        Optional.empty()),
                generationService,
                persistenceService,
                new CourseResponseAssembler());

        var response = service.createCourse(request());

        assertThat(providerCalled).isTrue();
        verify(persistenceService).persist(
                any(CourseRequestDto.class),
                org.mockito.ArgumentMatchers.argThat(facts ->
                        facts.candidates().size() == 1
                                && "candidate-1".equals(
                                facts.candidates().get(0).identity().candidateId())),
                any(CourseAiResultDto.class),
                org.mockito.ArgumentMatchers.argThat(metadata ->
                        metadata.generationReason()
                                == com.example.hangat.course.model.GenerationReason.INITIAL));
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).items().get(0).placeName()).isEqualTo("만장굴");
        assertThat(response.days().get(0).items().get(0).recommendationReason())
                .isEqualTo("한글 추천 이유");
    }

    private CourseRequestDto request() throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date": "2026-08-27",
                  "end_date": "2026-08-29",
                  "people": 2,
                  "budget_total": 500000,
                  "transport": "RENTAL_CAR",
                  "course_regions": [],
                  "course_styles": [{"code": "NATURE", "weight": 1}],
                  "course_place_preferences": []
                }
                """, CourseRequestDto.class);
    }

    private final class StubTourApiService extends TourApiService {
        @Override
        public List<TourPlaceDto> getTourPlaces() {
            try {
                return List.of(objectMapper.readValue("""
                        {"contentid":"candidate-1","title":"만장굴","addr1":"주소 미상",
                         "mapy":33.529,"mapx":126.771,"cat1":"A01"}
                        """, TourPlaceDto.class));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private static final class StubCongestionApiService extends CongestionApiService {
        @Override
        public List<CongestionDto> getCongestionData(String signguCd, String name) {
            return List.of();
        }
    }
}
