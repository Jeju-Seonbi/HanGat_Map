package com.example.hangat.course;

import com.example.hangat.course.model.CourseResponseDto;
import com.example.hangat.course.model.CourseStatus;
import com.example.hangat.course.model.CourseType;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.model.Transport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void postCoursesReturnsSnakeCaseUtf8JsonBody() throws Exception {
        CourseService courseService = mock(CourseService.class);
        when(courseService.createCourse(any())).thenReturn(response());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(courseService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        MvcResult result = mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content("""
                                {
                                  "start_date":"2026-08-27",
                                  "end_date":"2026-08-29",
                                  "people":2,
                                  "budget_total":500000,
                                  "transport":"RENTAL_CAR",
                                  "course_regions":[],
                                  "course_styles":[{"code":"NATURE","weight":1}],
                                  "course_place_preferences":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contract_version").value("1.0"))
                .andExpect(jsonPath("$.days[0].day_no").value(1))
                .andExpect(jsonPath("$.days[0].visit_date").value("2026-08-28"))
                .andExpect(jsonPath("$.days[0].items[0].candidate_id").value("candidate-1"))
                .andExpect(jsonPath("$.days[0].items[0].id").value(201))
                .andExpect(jsonPath("$.days[0].items[0].course_id").value(101))
                .andExpect(jsonPath("$.days[0].items[0].place_id").value(301))
                .andExpect(jsonPath("$.days[0].items[0].place_name").value("성산일출봉"))
                .andExpect(jsonPath("$.days[0].items[0].day_no").value(1))
                .andExpect(jsonPath("$.days[0].items[0].visit_date").value("2026-08-28"))
                .andExpect(jsonPath("$.days[0].items[0].category_name").value("관광지"))
                .andExpect(jsonPath("$.days[0].items[0].costs").isArray())
                .andExpect(jsonPath("$.days[0].items[0].costs").isEmpty())
                .andExpect(jsonPath("$.days[0].items[0].congestion_rate").value(22.5))
                .andExpect(jsonPath("$.days[0].items[0].congestion_level").value("QUIET"))
                .andExpect(jsonPath("$.accommodation.place_name").value("제주 숙소"))
                .andExpect(jsonPath("$.days[0].items[0].recommendation_reason")
                        .value("혼잡도가 낮고 동선이 좋아요."))
                .andExpect(jsonPath("$.days[0].items[0].congestion[0].rate").value(22.5))
                .andExpect(jsonPath("$.days[0].items[0].weather[0].forecast_date")
                        .value("2026-08-28"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseBody).contains("성산일출봉", "혼잡도가 낮고 동선이 좋아요.");
        assertThat(objectMapper.readTree(responseBody).path("days").isArray()).isTrue();
    }

    private CourseResponseDto response() {
        com.example.hangat.course.model.AccommodationDto accommodation;
        try {
            accommodation = objectMapper.readValue("""
                    {"source_code":"KAKAO_LOCAL","source_place_id":"stay-1",
                     "place_name":"제주 숙소","address":"제주 주소",
                     "latitude":33.4,"longitude":126.8}
                    """, com.example.hangat.course.model.AccommodationDto.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        CourseResponseDto.ItemDto item = new CourseResponseDto.ItemDto(
                201L, 101L, 301L,
                "candidate-1", "성산일출봉", "제주특별자치도 서귀포시 성산읍",
                33.458, 126.942, null,
                "관광지",
                new CourseResponseDto.TourCategoryDto("A01", null, null),
                "EAST", null, List.of("NATURE"), 1, 1,
                LocalDate.of(2026, 8, 28),
                LocalTime.of(9, 0), CourseResponseDto.ItemSource.AI_RECOMMENDED,
                "혼잡도가 낮고 동선이 좋아요.",
                List.of(),
                new BigDecimal("22.5"),
                com.example.hangat.course.model.CongestionLevel.QUIET,
                List.of(new CourseResponseDto.CongestionFactDto(
                        LocalDate.of(2026, 8, 28), new BigDecimal("22.5"),
                        com.example.hangat.course.model.CongestionLevel.QUIET)),
                List.of(new CourseResponseDto.WeatherFactDto(
                        LocalDate.of(2026, 8, 28), LocalTime.of(9, 0),
                        new BigDecimal("27.5"), 20, "0", "1",
                        new BigDecimal("2.1"), 65)));
        return new CourseResponseDto(
                101L, "1.0", CourseType.USER, GenerationReason.INITIAL, CourseStatus.READY,
                LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 29),
                2, 500000, Transport.RENTAL_CAR, accommodation,
                List.of(new CourseResponseDto.DayDto(
                        1, LocalDate.of(2026, 8, 28), List.of(item))));
    }
}
