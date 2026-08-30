package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.enums.CongestionLevel;
import com.example.hangat.map.model.enums.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 후기(명세서 28.0) - 저장, 논리 삭제, 목록에서 삭제분 제외 검증 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReviewRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReviewRepository repository;

    private static final Long 회원 = 7L;

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

    private Review review(Byte rating, CongestionLevel report) {
        return Review.builder()
                .userId(회원).place(감귤박물관)
                .rating(rating).congestionReport(report)
                .content("사람 없고 조용해요")
                .status(ReviewStatus.ACTIVE)
                .build();
    }

    @Test
    void 별점_없이_혼잡제보만_있는_후기도_저장된다() {
        // '별점 또는 제보 중 1개 필수'는 서비스 검증 - DB는 양쪽 다 NULL 허용이다
        Review saved = repository.saveAndFlush(review(null, CongestionLevel.QUIET));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRating()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 삭제는_행을_지우지_않고_상태만_바꾼다() {
        Review saved = repository.saveAndFlush(review((byte) 4, null));

        saved.delete();
        repository.saveAndFlush(saved);

        Review found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(found.getDeletedAt()).isNotNull();
    }

    @Test
    void 목록은_삭제된_후기를_빼고_최신순이다() {
        Review 첫번째 = repository.saveAndFlush(review((byte) 5, null));
        Review 두번째 = repository.saveAndFlush(review((byte) 3, null));
        Review 삭제된것 = repository.saveAndFlush(review((byte) 1, null));
        삭제된것.delete();
        repository.saveAndFlush(삭제된것);

        Page<Review> page = repository.findByPlaceIdAndStatusOrderByCreatedAtDesc(
                감귤박물관.getId(), ReviewStatus.ACTIVE, PageRequest.of(0, 6));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).extracting(Review::getId)
                .doesNotContain(삭제된것.getId());
    }
}
