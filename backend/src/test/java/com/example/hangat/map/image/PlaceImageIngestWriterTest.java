package com.example.hangat.map.image;

import com.example.hangat.map.image.model.PlaceImageItem;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.PlaceImage;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.PlaceImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 사진 저장 - 순서·대표 지정·중복 URL 방어·재적재 검증 (실측 응답 값 사용) */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PlaceImageIngestWriter.class)
class PlaceImageIngestWriterTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PlaceImageIngestWriter writer;

    @Autowired
    private PlaceImageRepository imageRepository;

    private Place 감귤박물관;

    @BeforeEach
    void setUp() {
        Region west = Region.builder().code("WEST").name("서부").displayOrder((byte) 1).build();
        PlaceCategory tourist = PlaceCategory.builder().code("TOURIST").name("관광지").build();
        em.persist(west);
        em.persist(tourist);
        감귤박물관 = em.persist(Place.builder()
                .region(west).primaryCategory(tourist)
                .name("감귤박물관").normalizedName("감귤박물관")
                .build());
        em.flush();
    }

    private PlaceImageItem item(String url, String name, String license) {
        return new PlaceImageItem("590415", url, url, name, license, "s");
    }

    @Test
    void 응답_순서대로_저장되고_첫_장이_대표가_된다() {
        // 값은 2026-08-30 실호출에서 가져왔다
        writer.saveChunk(List.of(new PlaceImageIngestWriter.Row(감귤박물관.getId(), List.of(
                item("https://tong.visitkorea.or.kr/cms/resource/31/3411131_image2_1.jpg", "전경_제주박물관 (8)", "Type3"),
                item("https://tong.visitkorea.or.kr/cms/resource/33/3411133_image2_1.jpg", "전경_제주박물관 (9)", "Type1")
        ))), "출처: 한국관광공사 국문 관광정보 서비스");

        List<PlaceImage> saved = imageRepository.findByPlaceIdOrderBySortOrder(감귤박물관.getId());

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getIsPrimary()).isTrue();
        assertThat(saved.get(1).getIsPrimary()).isFalse();
        assertThat(saved.get(0).getLicenseCode()).isEqualTo("Type3");
        assertThat(saved.get(0).getAttribution()).contains("한국관광공사");
    }

    @Test
    void 같은_URL이_두_번_오면_첫_장만_남는다() {
        // KTO 응답 중복 방어 - 그대로 넣으면 UK 위반으로 청크 전체가 죽는다
        String url = "https://tong.visitkorea.or.kr/cms/resource/31/3411131_image2_1.jpg";
        writer.saveChunk(List.of(new PlaceImageIngestWriter.Row(감귤박물관.getId(), List.of(
                item(url, "a", "Type3"), item(url, "b", "Type3")
        ))), "출처");

        assertThat(imageRepository.findByPlaceIdOrderBySortOrder(감귤박물관.getId())).hasSize(1);
    }

    @Test
    void 재적재하면_이전_사진이_교체된다() {
        writer.saveChunk(List.of(new PlaceImageIngestWriter.Row(감귤박물관.getId(), List.of(
                item("https://tong.visitkorea.or.kr/old.jpg", "구버전", "Type3")
        ))), "출처");
        writer.saveChunk(List.of(new PlaceImageIngestWriter.Row(감귤박물관.getId(), List.of(
                item("https://tong.visitkorea.or.kr/new.jpg", "신버전", "Type3")
        ))), "출처");

        List<PlaceImage> saved = imageRepository.findByPlaceIdOrderBySortOrder(감귤박물관.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getImageUrl()).endsWith("new.jpg");
    }
}
