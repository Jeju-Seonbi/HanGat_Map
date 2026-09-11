package com.example.hangat.map.hiddengem;

import com.example.hangat.map.model.entity.CongestionForecast;
import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.PlaceSourceMapping;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.model.enums.CongestionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 숨은 명소 판정 배치 - 집계 대상 여부는 최신 집중률 발표분으로 가르고, 발표분이 없으면 아무것도 바꾸지 않는다.
 * {@code replace = NONE} 사유는 PlaceRepositoryTest 참고.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({HiddenGemScoringService.class, HiddenGemRule.class})
class HiddenGemScoringServiceTest {

    private static final LocalDateTime 발표분 = LocalDateTime.of(2026, 9, 11, 0, 0);
    private static final LocalDateTime 대상일 = LocalDateTime.of(2026, 9, 12, 0, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private HiddenGemScoringService service;

    private Region east;
    private PlaceCategory tourist;
    private DataSource kto;
    private DataSource cnctr;

    @BeforeEach
    void setUp() {
        east = em.persist(Region.builder().code("EAST").name("동부").displayOrder((byte) 1).build());
        tourist = em.persist(PlaceCategory.builder().code("TOURIST").name("관광지").build());
        kto = em.persist(DataSource.builder().code("KTO").displayName("한국관광공사 TourAPI")
                .providerName("한국관광공사").attributionText("한국관광공사").displayOrder((short) 1).isActive(true).build());
        cnctr = em.persist(DataSource.builder().code("KTO_CNCTR").displayName("집중률")
                .providerName("한국관광공사").attributionText("한국관광공사").displayOrder((short) 2).isActive(true).build());
    }

    private Place ktoPlace(String name, boolean quality) {
        Place.PlaceBuilder builder = Place.builder().region(east).primaryCategory(tourist).name(name).normalizedName(name);
        if (quality) {
            builder.imageUrl("https://tong.visitkorea.or.kr/" + name + ".jpg")
                    .latitude(new BigDecimal("33.4")).longitude(new BigDecimal("126.7"))
                    .roadAddress("제주특별자치도 제주시 " + name).operatingHoursText("상시 개방");
        }
        Place place = em.persist(builder.build());
        em.persist(PlaceSourceMapping.builder().place(place).source(kto).sourcePlaceId("kto-" + name)
                .lastSyncedAt(LocalDateTime.of(2026, 9, 10, 0, 0)).build());
        return place;
    }

    private void forecast(Place place) {
        em.persist(CongestionForecast.builder().place(place).source(cnctr)
                .baseAt(발표분).forecastAt(대상일)
                .rate(new BigDecimal("55.00")).level(CongestionLevel.NORMAL)
                .fetchedAt(발표분).build());
    }

    @Test
    void 집계_대상이_아닌_품질_충족_관광지만_숨은_명소가_된다() {
        Place 가문이오름 = ktoPlace("가문이오름", true);
        Place 성산일출봉 = ktoPlace("성산일출봉", true);
        Place 사진없는곳 = ktoPlace("사진없는곳", false);
        forecast(성산일출봉);
        em.flush();

        HiddenGemScoringService.HiddenGemScoringResult result = service.score();
        em.flush();
        em.clear();

        assertThat(result.skipped()).isFalse();
        assertThat(result.evaluated()).isEqualTo(3);
        assertThat(result.hiddenGems()).isEqualTo(1);
        assertThat(result.famous()).isEqualTo(1);
        assertThat(result.baseAt()).isEqualTo(발표분);

        Place gem = em.find(Place.class, 가문이오름.getId());
        assertThat(gem.isHiddenGem()).isTrue();
        assertThat(gem.getHiddenGemScore()).isEqualByComparingTo("0.700");
        assertThat(gem.getHiddenGemAlgorithmVersion()).isEqualTo(HiddenGemRule.VERSION);
        assertThat(gem.getHiddenGemCalculatedAt()).isNotNull();

        Place famous = em.find(Place.class, 성산일출봉.getId());
        assertThat(famous.isHiddenGem()).isFalse();
        assertThat(famous.getHiddenGemScore()).isEqualByComparingTo("0.700");   // 점수는 판정과 무관하게 남긴다

        assertThat(em.find(Place.class, 사진없는곳.getId()).isHiddenGem()).isFalse();
    }

    @Test
    void 집중률_발표분이_없으면_판정을_건너뛰고_아무것도_바꾸지_않는다() {
        Place place = ktoPlace("가문이오름", true);
        em.flush();

        HiddenGemScoringService.HiddenGemScoringResult result = service.score();
        em.flush();
        em.clear();

        assertThat(result.skipped()).isTrue();
        assertThat(result.evaluated()).isZero();
        Place untouched = em.find(Place.class, place.getId());
        assertThat(untouched.isHiddenGem()).isFalse();
        assertThat(untouched.getHiddenGemAlgorithmVersion()).isNull();
    }

    @Test
    void 다시_돌리면_같은_결과를_덮어쓴다_멱등() {
        Place place = ktoPlace("가문이오름", true);
        forecast(ktoPlace("성산일출봉", true));
        em.flush();

        service.score();
        service.score();
        em.flush();
        em.clear();

        assertThat(em.find(Place.class, place.getId()).isHiddenGem()).isTrue();
        assertThat(service.score().hiddenGems()).isEqualTo(1);
    }

    @Test
    void 출처에서_사라진_매핑은_판정하지_않는다() {
        Place place = em.persist(Place.builder().region(east).primaryCategory(tourist).name("사라진곳").normalizedName("사라진곳")
                .imageUrl("x").latitude(BigDecimal.ONE).longitude(BigDecimal.ONE).roadAddress("주소").overview("소개").build());
        em.persist(PlaceSourceMapping.builder().place(place).source(kto).sourcePlaceId("kto-gone")
                .lastSyncedAt(LocalDateTime.of(2026, 9, 10, 0, 0)).isActive(false).build());
        forecast(ktoPlace("성산일출봉", true));
        em.flush();

        HiddenGemScoringService.HiddenGemScoringResult result = service.score();

        assertThat(result.evaluated()).isEqualTo(1);   // 성산일출봉만
        em.flush();
        em.clear();
        assertThat(em.find(Place.class, place.getId()).getHiddenGemAlgorithmVersion()).isNull();
    }
}
