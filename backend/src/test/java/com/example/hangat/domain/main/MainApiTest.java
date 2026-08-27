package com.example.hangat.domain.main;

import com.example.hangat.domain.congestion.CongestionForecastRepository;
import com.example.hangat.domain.congestion.model.CongestionForecast;
import com.example.hangat.domain.place.PlaceRepository;
import com.example.hangat.domain.place.model.Place;
import com.example.hangat.domain.place.model.PlaceCategory;
import com.example.hangat.domain.place.model.Region;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MainApiTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    @Autowired MockMvc mockMvc;
    @Autowired PlaceRepository placeRepository;
    @Autowired CongestionForecastRepository forecastRepository;

    @BeforeEach
    void seed() {
        forecastRepository.deleteAll();
        placeRepository.deleteAll();

        Place calm = save("혼인지", PlaceCategory.TOURIST, 23.0);
        Place moderate = save("새별오름", PlaceCategory.TOURIST, 41.0);
        Place crowded = save("성산일출봉", PlaceCategory.TOURIST, 83.0);   // 하드 컷 대상
        Place cafe = save("평대당", PlaceCategory.CAFE, 10.0);             // 관광지 아님 - 제외
    }

    private Place save(String name, PlaceCategory category, double rate) {
        Place place = placeRepository.save(Place.builder()
                .name(name)
                .region(Region.EAST)
                .category(category)
                .latitude(BigDecimal.valueOf(33.4))
                .longitude(BigDecimal.valueOf(126.9))
                .build());
        forecastRepository.save(CongestionForecast.builder()
                .place(place)
                .baseDate(DATE)
                .rate(rate)
                .build());
        return place;
    }

    @Test
    void 집중률_오름차순으로_관광지만_내려준다() throws Exception {
        mockMvc.perform(get("/main/calm-places").param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.result.length()").value(2))       // 혼잡 컷 + 카페 제외
                .andExpect(jsonPath("$.result[0].name").value("혼인지"))
                .andExpect(jsonPath("$.result[0].level").value("RELAXED"))
                .andExpect(jsonPath("$.result[1].name").value("새별오름"))
                .andExpect(jsonPath("$.result[1].levelLabel").value("보통"));
    }

    @Test
    void 혼잡_등급은_목록에서_제외된다() throws Exception {
        mockMvc.perform(get("/main/calm-places").param("date", DATE.toString()))
                .andExpect(jsonPath("$.result[?(@.name == '성산일출봉')]").isEmpty());
    }

    @Test
    void 예보가_없는_날짜는_3401을_돌려준다() throws Exception {
        mockMvc.perform(get("/main/calm-places").param("date", "2030-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3401));
    }

    @Test
    void limit_범위를_벗어나면_400() throws Exception {
        mockMvc.perform(get("/main/calm-places")
                        .param("date", DATE.toString())
                        .param("limit", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3000));
    }
}
