package com.example.hangat.map.review;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.review.model.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 방문 후기 (MAP-09) */
@Tag(name = "후기", description = "장소 방문 후기")
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "장소별 후기 목록", description = "삭제된 후기는 빠지고 최신순. 없는 장소면 PLACE_NOT_FOUND(3201).")
    @GetMapping("/places/{placeId}/reviews")
    public BaseResponse<PageResponse<ReviewResponse>> getReviews(
            @PathVariable("placeId") Long placeId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "6") int size) {
        return BaseResponse.success(reviewService.getReviews(placeId, page, size));
    }
}
