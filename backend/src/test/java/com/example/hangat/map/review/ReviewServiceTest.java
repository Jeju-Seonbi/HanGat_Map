package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.entity.ReviewImage;
import com.example.hangat.map.model.enums.ReviewStatus;
import com.example.hangat.map.review.model.ReviewCreateRequest;
import com.example.hangat.map.review.model.ReviewResponse;
import com.example.hangat.user.model.User;
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
    void 작성자_닉네임이_함께_나온다() {
        User user = em.persist(User.signUpWithSocial("nick@test.local", "제주도침략자"));
        em.persist(Review.builder()
                .userId(user.getId()).place(감귤박물관)
                .rating((byte) 5).status(ReviewStatus.ACTIVE)
                .build());
        em.flush();

        PageResponse<ReviewResponse> page = service.getReviews(감귤박물관.getId(), 0, 6);

        assertThat(page.getContent().get(0).getNickname()).isEqualTo("제주도침략자");
    }

    @Test
    void 유저가_없으면_닉네임은_null이다() {
        review("작성자가 탈퇴한 후기");   // userId 7L - users 에 없는 id
        em.flush();

        PageResponse<ReviewResponse> page = service.getReviews(감귤박물관.getId(), 0, 6);

        assertThat(page.getContent().get(0).getNickname()).isNull();
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

    private ReviewCreateRequest request(Byte rating, String report, String... urls) {
        ReviewCreateRequest req = new ReviewCreateRequest();
        set(req, "rating", rating);
        set(req, "congestionReport", report);
        set(req, "content", "한 줄 후기");
        set(req, "imageUrls", urls.length == 0 ? null : java.util.List.of(urls));
        return req;
    }

    private static void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 작성하면_장소_평점_요약이_갱신된다() {
        service.create(감귤박물관.getId(), 7L, request((byte) 4, null));
        service.create(감귤박물관.getId(), 8L, request((byte) 5, null));

        assertThat(감귤박물관.getReviewCount()).isEqualTo(2);
        assertThat(감귤박물관.getRatingAvg()).isEqualByComparingTo("4.50");
    }

    @Test
    void 제보만_한_후기는_건수에는_들지만_평균은_안_바꾼다() {
        service.create(감귤박물관.getId(), 7L, request((byte) 4, null));
        service.create(감귤박물관.getId(), 8L, request(null, "QUIET"));

        assertThat(감귤박물관.getReviewCount()).isEqualTo(2);
        assertThat(감귤박물관.getRatingAvg()).isEqualByComparingTo("4.00");
    }

    @Test
    void 별점도_제보도_없으면_거부된다() {
        assertThatThrownBy(() -> service.create(감귤박물관.getId(), 7L, request(null, null)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 사진_6장은_거부된다() {
        assertThatThrownBy(() -> service.create(감귤박물관.getId(), 7L,
                request((byte) 4, null, "u1", "u2", "u3", "u4", "u5", "u6")))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 남의_후기는_지울_수_없다() {
        ReviewResponse mine = service.create(감귤박물관.getId(), 7L, request((byte) 4, null));

        assertThatThrownBy(() -> service.delete(mine.getId(), 999L))
                .isInstanceOf(BaseException.class);

        // 본인이면 지워지고 요약이 되돌아간다
        service.delete(mine.getId(), 7L);
        assertThat(감귤박물관.getReviewCount()).isZero();
    }
}
