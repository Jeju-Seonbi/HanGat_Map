package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.entity.ReviewImage;
import com.example.hangat.map.model.enums.ReviewStatus;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.repository.ReviewImageRepository;
import com.example.hangat.map.repository.ReviewRepository;
import com.example.hangat.map.review.model.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 방문 후기 (MAP-09) */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final PlaceRepository placeRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewImageRepository imageRepository,
                         PlaceRepository placeRepository) {
        this.reviewRepository = reviewRepository;
        this.imageRepository = imageRepository;
        this.placeRepository = placeRepository;
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

        return PageResponse.from(reviews.map(r ->
                ReviewResponse.from(r, imagesByReview.getOrDefault(r.getId(), List.of()))));
    }
}
