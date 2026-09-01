package com.example.hangat.course.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.course.model.CourseDetailResponse;
import com.example.hangat.course.model.CourseSummaryResponse;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.entity.CourseItem;
import com.example.hangat.course.model.enums.CourseType;
import com.example.hangat.course.model.enums.Transport;
import com.example.hangat.course.repository.CourseItemRepository;
import com.example.hangat.course.repository.CourseRepository;
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
import com.example.hangat.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코스 조회·관리 API 검증 (상세 / 저장 목록 / 이름변경 / 삭제).
 *
 * <p>인증이 걸린 경로는 서비스를 직접 불러 검증한다 - 컨트롤러는 CurrentUser로 id를 꺼내
 * 그대로 넘기는 얇은 층이라, MockMvc로 SecurityContext를 꾸미는 것보다 권한 규칙 자체를
 * 찌르는 편이 정확하다. 공개 경로(상세)와 로그인 요구 응답은 MockMvc로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseQueryApiTest {

    private static final LocalDate 출발일 = LocalDate.of(2026, 9, 20);
    private static final LocalDateTime 발표버전 = LocalDateTime.of(2026, 9, 19, 0, 0);

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired CourseQueryService queryService;
    @Autowired CourseCommandService commandService;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseItemRepository itemRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired CongestionForecastRepository forecastRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired PlaceCategoryRepository categoryRepository;

    private PlaceCategory tourist;
    private DataSource source;
    private Region east;

    private User 주인;
    private User 남;
    private Course 임시코스;      // 소유자 없음 - 공개
    private Course 저장코스;      // 주인 소유

    @BeforeEach
    void seed() {
        forecastRepository.deleteAll();

        east = regionRepository.findByCode("EAST")
                .orElseGet(() -> regionRepository.save(Region.builder()
                        .code("EAST").name("동부").displayOrder((byte) 3).build()));
        tourist = categoryRepository.findByCode("TOURIST")
                .orElseGet(() -> categoryRepository.save(PlaceCategory.builder()
                        .code("TOURIST").name("관광지").build()));
        source = em.find(DataSource.class, "KTO_CNCTR");
        if (source == null) {
            source = DataSource.builder()
                    .code("KTO_CNCTR").displayName("한국관광공사 관광지별 집중률")
                    .providerName("한국관광공사").attributionText("한국관광공사")
                    .displayOrder((short) 2).isActive(true)
                    .build();
            em.persist(source);
        }

        주인 = 회원가입("owner@hangat.local");
        남 = 회원가입("stranger@hangat.local");

        Place 성산 = place("성산일출봉", 33.4581, 126.9425, "35.00");
        Place 혼인지 = place("혼인지", 33.4335, 126.8930, "23.00");
        Place 표선 = place("표선해수욕장", 33.3260, 126.8370, "41.00");

        임시코스 = course(null, null);
        item(임시코스, 성산, 1, 1, 출발일, "80.00");    // 스냅숏 80 vs 지금 35 - 예보가 내려간 상태
        item(임시코스, 혼인지, 1, 2, 출발일, null);
        item(임시코스, 표선, 2, 1, 출발일.plusDays(1), null);
        임시코스.markReady();

        저장코스 = course(null, null);
        item(저장코스, 혼인지, 1, 1, 출발일, null);
        저장코스.markReady();
        저장코스.markSaved(주인, "동부 한산 코스");

        em.flush();
    }

    private User 회원가입(String email) {
        User user = User.signUpWithEmail(email, "bcrypt-해시-자리", "조회테스터-" + email.charAt(0));
        em.persist(user);
        return user;
    }

    private Place place(String name, double lat, double lng, String rate) {
        Place place = placeRepository.save(Place.builder()
                .name(name).normalizedName(name)
                .region(east).primaryCategory(tourist)
                .latitude(BigDecimal.valueOf(lat)).longitude(BigDecimal.valueOf(lng))
                .isGoodPrice(false).isHiddenGem(false).reviewCount(0)
                .build());
        for (int day = 0; day < 2; day++) {
            forecastRepository.save(CongestionForecast.of(place, source,
                    PlaceNameNormalizer.jejuDayToUtc(출발일.plusDays(day)), 발표버전,
                    new BigDecimal(rate)));
        }
        return place;
    }

    private Course course(User owner, String title) {
        return courseRepository.save(Course.builder()
                .user(owner).title(title)
                .startDate(출발일).endDate(출발일.plusDays(1))
                .transport(Transport.RENTAL_CAR)
                .averageCongestionRate(new BigDecimal("33.00"))
                .build());
    }

    /** plannedRate가 있으면 그 값으로 별도 스냅숏 예보를 만들어 붙인다(과거 발표 버전 흉내). */
    private CourseItem item(Course course, Place place, int dayNo, int position,
                            LocalDate date, String plannedRate) {
        CongestionForecast snapshot = null;
        if (plannedRate != null) {
            snapshot = forecastRepository.save(CongestionForecast.of(place, source,
                    PlaceNameNormalizer.jejuDayToUtc(date), 발표버전.minusDays(3),
                    new BigDecimal(plannedRate)));
        }
        return itemRepository.save(CourseItem.builder()
                .course(course).place(place)
                .dayNo((short) dayNo).position((short) position)
                .visitDate(date)
                .plannedCongestionForecast(snapshot)
                .recommendationReason("한산해요")
                .build());
    }

    @Test
    void 상세는_일차별로_묶여_나오고_혼잡을_두_값으로_준다() {
        CourseDetailResponse detail = queryService.detail(임시코스.getId(), null);

        assertThat(detail.days()).hasSize(2);
        assertThat(detail.days().get(0).dayNo()).isEqualTo(1);
        assertThat(detail.days().get(0).items()).hasSize(2);
        assertThat(detail.days().get(1).items()).hasSize(1);
        assertThat(detail.durationText()).isEqualTo("1박 2일");

        CourseDetailResponse.ItemDto 성산 = detail.days().get(0).items().get(0);
        assertThat(성산.placeName()).isEqualTo("성산일출봉");
        assertThat(성산.congestionRate()).isEqualTo(35.0);          // 지금 예보
        assertThat(성산.congestionLabel()).isEqualTo("여유");
        assertThat(성산.plannedCongestionRate()).isEqualTo(80.0);   // 저장 시점 스냅숏
        assertThat(성산.regionName()).isEqualTo("동부");
        assertThat(성산.latitude()).isNotNull();
    }

    @Test
    void 소유자_없는_임시_코스는_비로그인도_열_수_있다() throws Exception {
        mockMvc.perform(get("/courses/{id}", 임시코스.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.result.days.length()").value(2))
                .andExpect(jsonPath("$.result.days[0].items[0].place_name").value("성산일출봉"))
                .andExpect(jsonPath("$.result.days[0].items[0].planned_congestion_rate").value(80.0))
                .andExpect(jsonPath("$.result.editable").value(true))
                .andExpect(jsonPath("$.result.duration_text").value("1박 2일"));
    }

    @Test
    void 남의_저장_코스는_열_수_없다() {
        assertThatThrownBy(() -> queryService.detail(저장코스.getId(), 남.getId()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("본인의 코스만");
        assertThatThrownBy(() -> queryService.detail(저장코스.getId(), null))
                .isInstanceOf(BaseException.class);

        CourseDetailResponse mine = queryService.detail(저장코스.getId(), 주인.getId());
        assertThat(mine.title()).isEqualTo("동부 한산 코스");
        assertThat(mine.editable()).isTrue();
    }

    @Test
    void 샘플_코스는_공개지만_편집할_수_없다() {
        Course 샘플 = courseRepository.save(Course.builder()
                .courseType(CourseType.SAMPLE).title("동부 샘플")
                .startDate(출발일).endDate(출발일.plusDays(1))
                .transport(Transport.RENTAL_CAR)
                .build());
        샘플.markReady();
        em.flush();

        CourseDetailResponse detail = queryService.detail(샘플.getId(), 주인.getId());
        assertThat(detail.editable()).isFalse();   // 공유 자산이라 남이 못 바꾼다
    }

    @Test
    void 삭제된_코스는_존재_여부까지_숨긴다() throws Exception {
        commandService.delete(저장코스.getId(), 주인.getId());
        em.flush();

        assertThatThrownBy(() -> queryService.detail(저장코스.getId(), 주인.getId()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("존재하지 않는 코스");
        mockMvc.perform(get("/courses/{id}", 저장코스.getId()))
                .andExpect(jsonPath("$.code").value(3301));
    }

    @Test
    void 저장_목록은_내_SAVED_코스만_준다() {
        List<CourseSummaryResponse> mine = queryService
                .savedCourses(주인.getId(), PageRequest.of(0, 10)).getContent();

        assertThat(mine).hasSize(1);
        CourseSummaryResponse card = mine.get(0);
        assertThat(card.title()).isEqualTo("동부 한산 코스");
        assertThat(card.placeCount()).isEqualTo(1);
        assertThat(card.highlightNames()).containsExactly("혼인지");
        assertThat(card.regionName()).isEqualTo("동부");
        assertThat(card.durationText()).isEqualTo("1박 2일");
        assertThat(card.savedAt()).isNotNull();
        assertThat(card.congestionLabel()).isEqualTo("여유");

        // 임시 코스(미저장)와 남의 목록에는 안 잡힌다
        assertThat(mine).noneMatch(c -> c.id().equals(임시코스.getId()));
        assertThat(queryService.savedCourses(남.getId(), PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    void 삭제하면_목록에서_사라지고_다시_지워도_성공한다() {
        commandService.delete(저장코스.getId(), 주인.getId());
        em.flush();

        assertThat(queryService.savedCourses(주인.getId(), PageRequest.of(0, 10)).getContent())
                .isEmpty();
        commandService.delete(저장코스.getId(), 주인.getId());   // 멱등 - 두 번 눌러도 오류 없음
        assertThat(courseRepository.findById(저장코스.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    void 이름은_본인만_바꿀_수_있다() {
        CourseSummaryResponse renamed = commandService
                .rename(저장코스.getId(), "  가을 동부 여행  ", 주인.getId());
        assertThat(renamed.title()).isEqualTo("가을 동부 여행");   // 앞뒤 공백 정리

        assertThatThrownBy(() -> commandService.rename(저장코스.getId(), "가로채기", 남.getId()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("본인의 코스만");
        // 소유자 없는 임시 코스는 이름 변경 대상이 아니다
        assertThatThrownBy(() -> commandService.rename(임시코스.getId(), "이름", 주인.getId()))
                .isInstanceOf(BaseException.class);
    }

    /**
     * 목록은 공개 목록에 없으므로 Security가 컨트롤러 앞에서 막는다(401 + 3002).
     * 컨트롤러의 LOGIN_REQUIRED 가드는 경로가 실수로 permitAll에 들어갔을 때를 위한 이중 방어다.
     */
    @Test
    void 저장_목록은_로그인이_필요하다() throws Exception {
        mockMvc.perform(get("/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
