package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.entity.ReviewImage;
import com.example.hangat.map.model.enums.CongestionLevel;
import com.example.hangat.map.model.enums.ReviewStatus;
import com.example.hangat.map.review.model.ReviewCreateRequest;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.repository.ReviewImageRepository;
import com.example.hangat.map.repository.ReviewRepository;
import com.example.hangat.map.review.model.ReviewResponse;
import com.example.hangat.user.model.User;
import com.example.hangat.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 방문 후기 (MAP-09) */
@Service
public class ReviewService {

    public static final int MAX_IMAGES = 5;
    private static final int MAX_CONTENT = 60;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewImageRepository imageRepository,
                         PlaceRepository placeRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.imageRepository = imageRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
    }

    /** 장소별 후기 목록 - 삭제분 제외, 최신순 */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviews(Long placeId, int page, int size) {
        if (!placeRepository.existsById(placeId)) {
            throw new BaseException(BaseResponseStatus.PLACE_NOT_FOUND);
        }
        Page<Review> reviews = reviewRepository.findByPlaceIdAndStatusOrderByCreatedAtDesc(
                placeId, ReviewStatus.ACTIVE, PageRequest.of(page, size));

        // 페이지(기본 6건)의 사진을 쿼리 한 번으로 - 후기마다 조회하면 N+1
        List<Long> ids = reviews.getContent().stream().map(Review::getId).toList();
        Map<Long, List<ReviewImage>> imagesByReview = ids.isEmpty() ? Map.of()
                : imageRepository.findByReviewIdInOrderBySortOrder(ids).stream()
                        .collect(Collectors.groupingBy(i -> i.getReview().getId()));

        Map<Long, String> nicknames = nicknamesOf(
                reviews.getContent().stream().map(Review::getUserId).toList());
        return PageResponse.from(reviews.map(r ->
                ReviewResponse.from(r, imagesByReview.getOrDefault(r.getId(), List.of()),
                        nicknames.get(r.getUserId()))));
    }

    /** 작성자 닉네임 - 페이지당 쿼리 한 번(IN). 탈퇴 등으로 유저가 없으면 맵에서 빠져 null 로 내려간다 */
    private Map<Long, String> nicknamesOf(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .filter(u -> u.getNickname() != null)
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    @Transactional
    public ReviewResponse create(Long placeId, Long userId, ReviewCreateRequest req) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.PLACE_NOT_FOUND));
        validate(req);

        Review review = reviewRepository.save(Review.builder()
                .userId(userId)
                .place(place)
                .rating(req.getRating())
                .congestionReport(parseReport(req.getCongestionReport()))
                .content(req.getContent())
                .status(ReviewStatus.ACTIVE)
                .build());

        List<String> urls = req.getImageUrls() == null ? List.of() : req.getImageUrls();
        List<ReviewImage> images = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            images.add(imageRepository.save(ReviewImage.builder()
                    .review(review)
                    // 로컬 스토리지는 URL 마지막 조각이 곧 파일 키다 - S3 전환 시 업로드 API 가 키를 준다
                    .storageKey(url.substring(url.lastIndexOf('/') + 1))
                    .imageUrl(url)
                    .sortOrder(i)
                    .build()));
        }

        refreshSummary(place);
        return ReviewResponse.from(review, images, nicknamesOf(List.of(userId)).get(userId));
    }

    @Transactional
    public void delete(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getStatus() == ReviewStatus.ACTIVE)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.REVIEW_NOT_FOUND));
        // 본인 확인 - 다른 사람 후기 id 를 넣어 지우는 것을 막는다
        if (!review.getUserId().equals(userId)) {
            throw new BaseException(BaseResponseStatus.REVIEW_FORBIDDEN);
        }
        review.delete();
        refreshSummary(review.getPlace());
    }

    private void validate(ReviewCreateRequest req) {
        boolean noRating = req.getRating() == null;
        boolean noReport = req.getCongestionReport() == null || req.getCongestionReport().isBlank();
        if (noRating && noReport) {
            throw new BaseException(BaseResponseStatus.REVIEW_RATING_OR_REPORT_REQUIRED);
        }
        if (!noRating && (req.getRating() < 1 || req.getRating() > 5)) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
        if (req.getContent() != null && req.getContent().length() > MAX_CONTENT) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
        if (req.getImageUrls() != null && req.getImageUrls().size() > MAX_IMAGES) {
            throw new BaseException(BaseResponseStatus.REVIEW_TOO_MANY_IMAGES);
        }
    }

    private CongestionLevel parseReport(String report) {
        if (report == null || report.isBlank()) {
            return null;
        }
        try {
            return CongestionLevel.valueOf(report);
        } catch (IllegalArgumentException e) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
    }

    /** places 의 평점 요약(비정규화) 갱신 - 별점 없는 후기는 평균에서 빠지고 건수에는 들어간다 */
    private void refreshSummary(Place place) {
        Object[] row = (Object[]) reviewRepository.summarize(place.getId())[0];
        Double avg = (Double) row[0];
        long count = (Long) row[1];
        place.updateReviewSummary(
                avg == null ? null : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP),
                (int) count);
    }
}
