package com.example.hangat.domain.main;

import com.example.hangat.map.model.entity.CongestionForecast;
import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.CongestionForecastRepository;
import com.example.hangat.map.repository.PlaceCategoryRepository;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.repository.RegionRepository;
import com.example.hangat.map.service.PlaceNameNormalizer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메인 "오늘의 한산 장소"(MAIN_001) API 검증.
 *
 * <p><b>2026-08-24 통합</b>: 데이터 계층이 {@code map} 도메인으로 바뀌었다.
 * 권역·카테고리가 enum에서 마스터 테이블로, 예보 시각이 날짜에서 UTC DATETIME으로 바뀌었지만
 * <b>화면 계약(JSON 응답)은 그대로여야 한다</b> - 그래서 단언은 손대지 않았다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MainApiTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime 발표버전 = LocalDateTime.of(2026, 8, 24, 0, 0);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired PlaceRepository placeRepository;
    @Autowired CongestionForecastRepository forecastRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired PlaceCategoryRepository categoryRepository;

    private Region east;
    private PlaceCategory tourist;
    private PlaceCategory cafe;
    private DataSource source;

    @BeforeEach
    void seed() {
        forecastRepository.deleteAll();

        // 마스터는 MapMasterDataInitializer가 채우지만, 같은 H2를 쓰는 @DataJpaTest가
        // 스키마를 다시 만들면 날아간다 - 실행 순서에 기대지 않도록 없으면 만든다
        east = regionRepository.findByCode("EAST")
                .orElseGet(() -> regionRepository.save(Region.builder()
                        .code("EAST").name("동부").displayOrder((byte) 3).build()));
        tourist = categoryRepository.findByCode("TOURIST")
                .orElseGet(() -> categoryRepository.save(PlaceCategory.builder()
                        .code("TOURIST").name("관광지").build()));
        cafe = categoryRepository.findByCode("CAFE")
                .orElseGet(() -> categoryRepository.save(PlaceCategory.builder()
                        .code("CAFE").name("카페").build()));
        source = em.find(DataSource.class, "KTO_CNCTR");
        if (source == null) {
            source = DataSource.builder()
                    .code("KTO_CNCTR").displayName("한국관광공사 관광지별 집중률")
                    .providerName("한국관광공사").attributionText("한국관광공사")
                    .displayOrder((short) 2).isActive(true)
                    .build();
            em.persist(source);
        }

        save("혼인지", tourist, "23.00");
        save("새별오름", tourist, "41.00");
        save("성산일출봉", tourist, "83.00");   // 하드 컷 대상
        save("평대당", cafe, "10.00");          // 관광지 아님 - 제외
        em.flush();
    }

    private void save(String name, PlaceCategory category, String rate) {
        Place place = placeRepository.save(Place.builder()
                .name(name).normalizedName(name)
                .region(east).primaryCategory(category)
                .latitude(BigDecimal.valueOf(33.4))
                .longitude(BigDecimal.valueOf(126.9))
                .isGoodPrice(false).isHiddenGem(false).reviewCount(0)
                .build());
        forecastRepository.save(CongestionForecast.of(
                place, source, PlaceNameNormalizer.jejuDayToUtc(DATE), 발표버전, new BigDecimal(rate)));
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
    void 권역과_분류는_마스터_테이블의_표시명으로_나간다() throws Exception {
        // enum label에서 테이블 name으로 바뀐 부분 - 값이 비면 프론트 카드에 빈칸이 생긴다
        mockMvc.perform(get("/main/calm-places").param("date", DATE.toString()))
                .andExpect(jsonPath("$.result[0].regionLabel").value("동부"))
                .andExpect(jsonPath("$.result[0].categoryLabel").value("관광지"));
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
