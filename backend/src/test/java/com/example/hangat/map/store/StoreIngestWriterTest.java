package com.example.hangat.map.store;

import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.service.RegionResolver;
import com.example.hangat.map.store.SbizStoreClient.StoreItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 상가 저장 - 신규 삽입·bizesId 멱등·권역 제외 검증 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({StoreIngestWriter.class, RegionResolver.class})
class StoreIngestWriterTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private StoreIngestWriter writer;

    @Autowired
    private PlaceRepository placeRepository;

    @BeforeEach
    void setUp() {
        em.persist(Region.builder().code("WEST").name("서부").displayOrder((byte) 1).build());
        em.persist(PlaceCategory.builder().code("CAFE").name("카페").build());
        em.persist(PlaceCategory.builder().code("CONVENIENCE").name("편의점").build());
        em.persist(DataSource.builder()
                .code("SBIZ").displayName("소상공인 상가정보").providerName("소상공인시장진흥공단")
                .attributionText("출처: 소상공인시장진흥공단 상가(상권)정보")
                .displayOrder((short) 3).isActive(true).build());
        em.flush();
    }

    private StoreItem item(String bizesId, String name, String address) {
        return new StoreItem(bizesId, name, address, null,
                new BigDecimal("33.48"), new BigDecimal("126.40"));
    }

    @Test
    void 신규_상가가_카테고리와_함께_들어간다() {
        StoreIngestWriter.Outcome outcome =
                writer.upsert(item("SB001", "애월커피", "제주특별자치도 제주시 애월읍 하귀9길 2"), "CAFE");

        assertThat(outcome).isEqualTo(StoreIngestWriter.Outcome.INSERTED);
        Place saved = placeRepository.findByNormalizedName("애월커피").get(0);
        assertThat(saved.getPrimaryCategory().getCode()).isEqualTo("CAFE");
        assertThat(saved.getRegion().getCode()).isEqualTo("WEST");
        assertThat(saved.getLatitude()).isEqualByComparingTo("33.48");
        assertThat(saved.isGoodPrice()).isFalse();
    }

    @Test
    void 같은_bizesId는_재실행해도_건너뛴다() {
        writer.upsert(item("SB001", "애월커피", "제주시 애월읍 하귀9길 2"), "CAFE");

        StoreIngestWriter.Outcome outcome =
                writer.upsert(item("SB001", "애월커피", "제주시 애월읍 하귀9길 2"), "CAFE");

        assertThat(outcome).isEqualTo(StoreIngestWriter.Outcome.ALREADY);
        assertThat(placeRepository.findByNormalizedName("애월커피")).hasSize(1);
    }

    @Test
    void 도로명이_없으면_지번_주소로_권역을_찾는다() {
        StoreItem 지번만 = new StoreItem("SB002", "하귀마트", null, "제주시 애월읍 하귀리 100",
                new BigDecimal("33.48"), new BigDecimal("126.40"));

        assertThat(writer.upsert(지번만, "CONVENIENCE")).isEqualTo(StoreIngestWriter.Outcome.INSERTED);
        assertThat(placeRepository.findByNormalizedName("하귀마트").get(0).getRegion().getCode())
                .isEqualTo("WEST");
    }

    @Test
    void 추자도는_권역_밖이라_넣지_않는다() {
        StoreIngestWriter.Outcome outcome =
                writer.upsert(item("SB003", "추자슈퍼", "제주시 추자면 대서리 1"), "CONVENIENCE");

        assertThat(outcome).isEqualTo(StoreIngestWriter.Outcome.NO_REGION);
        assertThat(placeRepository.findByNormalizedName("추자슈퍼")).isEmpty();
    }
}
