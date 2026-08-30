package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.PlaceImage;
import com.example.hangat.map.model.entity.Region;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 장소 사진(명세서 11.0) - 순서 조회, 중복 방지, 재적재 검증 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PlaceImageRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PlaceImageRepository repository;

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

    private PlaceImage image(int order, String url) {
        return PlaceImage.builder()
                .place(감귤박물관)
                .imageUrl(url)
                .urlHash("hash-" + url)
                .licenseCode("Type3")
                .attribution("한국관광공사")
                .sortOrder(order)
                .isPrimary(order == 0)
                .build();
    }

    @Test
    void 순서대로_조회된다() {
        // 역순으로 저장해도 sort_order 순으로 나와야 한다
        repository.save(image(1, "https://tong.visitkorea.or.kr/b.jpg"));
        repository.save(image(0, "https://tong.visitkorea.or.kr/a.jpg"));
        em.flush();

        List<PlaceImage> found = repository.findByPlaceIdOrderBySortOrder(감귤박물관.getId());

        assertThat(found).extracting(PlaceImage::getSortOrder).containsExactly(0, 1);
        assertThat(found.get(0).getIsPrimary()).isTrue();
    }

    @Test
    void 같은_장소에_같은_사진은_중복_저장이_막힌다() {
        repository.saveAndFlush(image(0, "https://tong.visitkorea.or.kr/a.jpg"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(image(1, "https://tong.visitkorea.or.kr/a.jpg")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 재적재는_선삭제_후_같은_순서로_넣어도_UK에_안_걸린다() {
        // 재적재 = 지우고 같은 순서로 다시 넣기. deleteByPlace 없이는 UK 충돌로 죽는다
        repository.save(image(0, "https://tong.visitkorea.or.kr/a.jpg"));
        repository.save(image(1, "https://tong.visitkorea.or.kr/b.jpg"));
        em.flush();

        repository.deleteByPlace(감귤박물관);
        repository.save(image(0, "https://tong.visitkorea.or.kr/new.jpg"));
        em.flush();

        List<PlaceImage> found = repository.findByPlaceIdOrderBySortOrder(감귤박물관.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getImageUrl()).endsWith("new.jpg");
    }

    @Test
    void 장소를_지우면_사진도_같이_지워진다() {
        // @OnDelete(CASCADE) 확인
        repository.saveAndFlush(image(0, "https://tong.visitkorea.or.kr/a.jpg"));
        Long placeId = 감귤박물관.getId();
        em.clear();

        em.getEntityManager().createQuery("delete from Place p where p.id = :id")
                .setParameter("id", placeId).executeUpdate();

        assertThat(repository.findByPlaceIdOrderBySortOrder(placeId)).isEmpty();
    }
}
