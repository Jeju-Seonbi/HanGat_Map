package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.map.review.model.ReviewCreateRequest;
import com.example.hangat.map.review.model.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "후기 작성", description = "회원 전용. 별점 또는 혼잡 제보 중 1개 필수, 한줄 60자, 사진 최대 5장.")
    @PostMapping("/places/{placeId}/reviews")
    public BaseResponse<ReviewResponse> create(@PathVariable("placeId") Long placeId,
                                               @RequestBody ReviewCreateRequest request,
                                               Authentication authentication) {
        return BaseResponse.success(reviewService.create(placeId, currentUserId(authentication), request));
    }

    @Operation(summary = "후기 삭제", description = "작성자 본인만. 행은 남기고 상태만 DELETED 로 바꾼다.")
    @DeleteMapping("/reviews/{reviewId}")
    public BaseResponse<Void> delete(@PathVariable("reviewId") Long reviewId,
                                     Authentication authentication) {
        reviewService.delete(reviewId, currentUserId(authentication));
        return BaseResponse.success(null);
    }

    /** JWT 필터(auth 브랜치)가 principal 에 Long userId 를 넣는다 - 그 계약에 맞춘다 */
    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BaseException(BaseResponseStatus.LOGIN_REQUIRED);
        }
        return userId;
    }
}
