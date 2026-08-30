package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.entity.ReviewImage;
import com.example.hangat.map.model.enums.ReviewStatus;
import com.example.hangat.map.review.model.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 후기 목록 조회 - 페이징, 사진 포함, 없는 장소 방어 검증 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ReviewService.class)
class ReviewServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReviewService service;

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

    private Review review(String content) {
        return em.persist(Review.builder()
                .userId(7L).place(감귤박물관)
                .rating((byte) 4).content(content)
                .status(ReviewStatus.ACTIVE)
                .build());
    }

    @Test
    void 후기와_사진이_함께_나온다() {
        Review r = review("조용하고 좋아요");
        em.persist(ReviewImage.builder()
                .review(r).storageKey("k1").imageUrl("https://cdn.hangat.dev/1.jpg").sortOrder(0).build());
        em.flush();

        PageResponse<ReviewResponse> page = service.getReviews(감귤박물관.getId(), 0, 6);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getImageUrls()).containsExactly("https://cdn.hangat.dev/1.jpg");
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(7L);
    }

    @Test
    void 페이지_크기를_넘으면_다음_페이지로_나뉜다() {
        for (int i = 0; i < 7; i++) {
            review("후기 " + i);
        }
        em.flush();

        PageResponse<ReviewResponse> first = service.getReviews(감귤박물관.getId(), 0, 6);

        assertThat(first.getContent()).hasSize(6);
        assertThat(first.getTotalElements()).isEqualTo(7);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 없는_장소면_PLACE_NOT_FOUND다() {
        assertThatThrownBy(() -> service.getReviews(999_999L, 0, 6))
                .isInstanceOf(BaseException.class);
    }
}
