package com.example.hangat.map.review.model;

import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.entity.ReviewImage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 후기 한 건 - 목록 응답용. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewResponse {

    private final Long id;
    private final Long userId;
    /** 작성자 닉네임. 탈퇴 등으로 유저가 없으면 null - 화면이 대체 표기한다 */
    private final String nickname;
    /** null = 별점 없이 혼잡 제보만 한 후기 */
    private final Byte rating;
    /** QUIET/NORMAL/CROWDED 또는 null */
    private final String congestionReport;
    private final String content;
    private final List<String> imageUrls;
    private final LocalDateTime createdAt;

    public static ReviewResponse from(Review review, List<ReviewImage> images, String nickname) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .nickname(nickname)
                .rating(review.getRating())
                .congestionReport(review.getCongestionReport() == null
                        ? null : review.getCongestionReport().name())
                .content(review.getContent())
                .imageUrls(images.stream().map(ReviewImage::getImageUrl).toList())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
