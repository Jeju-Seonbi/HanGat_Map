package com.example.hangat.domain.alternative;

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
 * 대안 장소 API(#과밀지역우회) 검증.
 * 기준: 성산일출봉(혼잡 83) - 실좌표를 써서 거리 컷까지 실세계 값으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AlternativeApiTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime 발표버전 = LocalDateTime.of(2026, 8, 29, 0, 0);

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

    private Long baseId;        // 성산일출봉 (혼잡)
    private Long nearCalmId;    // 혼인지 (~5.3km, 여유)

    @BeforeEach
    void seed() {
        forecastRepository.deleteAll();

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

        baseId = save("성산일출봉", tourist, 33.4581, 126.9425, "83.00");
        nearCalmId = save("혼인지", tourist, 33.4335, 126.8930, "23.00");    // ~5.3km 여유
        save("광치기해변", tourist, 33.4515, 126.9246, "45.00");             // ~1.8km 보통
        save("표선해수욕장", tourist, 33.4581, 126.9425, "78.00");           // 근처지만 혼잡 - 컷
        save("협재해수욕장", tourist, 33.3940, 126.2400, "10.00");           // ~65km - 거리 컷
        save("성산카페", cafe, 33.4500, 126.9300, "5.00");                   // 카테고리 다름 - 컷
        em.flush();
    }

    private Long save(String name, PlaceCategory category, double lat, double lng, String rate) {
        Place place = placeRepository.save(Place.builder()
                .name(name).normalizedName(name)
                .region(east).primaryCategory(category)
                .latitude(BigDecimal.valueOf(lat))
                .longitude(BigDecimal.valueOf(lng))
                .isGoodPrice(false).isHiddenGem(false).reviewCount(0)
                .build());
        forecastRepository.save(CongestionForecast.of(
                place, source, PlaceNameNormalizer.jejuDayToUtc(DATE), 발표버전, new BigDecimal(rate)));
        return place.getId();
    }

    @Test
    void 같은_카테고리_반경내_저혼잡만_집중률_오름차순으로_준다() throws Exception {
        mockMvc.perform(get("/places/{id}/alternatives", baseId).param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.result.length()").value(2))   // 혼인지 + 광치기 (혼잡·원거리·카페 컷)
                .andExpect(jsonPath("$.result[0].name").value("혼인지"))
                .andExpect(jsonPath("$.result[0].levelLabel").value("여유"))
                .andExpect(jsonPath("$.result[0].distanceKm").value(5.3))
                .andExpect(jsonPath("$.result[1].name").value("광치기해변"))
                .andExpect(jsonPath("$.result[?(@.name == '협재해수욕장')]").isEmpty())
                .andExpect(jsonPath("$.result[?(@.name == '표선해수욕장')]").isEmpty())
                .andExpect(jsonPath("$.result[?(@.name == '성산카페')]").isEmpty());
    }

    @Test
    void exclude에_있는_장소는_대안에서_빠진다() throws Exception {
        mockMvc.perform(get("/places/{id}/alternatives", baseId)
                        .param("date", DATE.toString())
                        .param("exclude", nearCalmId.toString()))
                .andExpect(jsonPath("$.result[?(@.name == '혼인지')]").isEmpty())
                .andExpect(jsonPath("$.result[0].name").value("광치기해변"));
    }

    @Test
    void 없는_장소면_3201을_돌려준다() throws Exception {
        mockMvc.perform(get("/places/{id}/alternatives", 999999).param("date", DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3201));
    }

    @Test
    void 예보가_없는_날짜는_3401을_돌려준다() throws Exception {
        mockMvc.perform(get("/places/{id}/alternatives", baseId).param("date", "2030-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3401));
    }
}
