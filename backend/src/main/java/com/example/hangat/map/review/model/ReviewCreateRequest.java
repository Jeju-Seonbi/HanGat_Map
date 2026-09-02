package com.example.hangat.map.review.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 후기 작성 요청. 별점 또는 혼잡 제보 중 하나는 있어야 한다(서비스 검증). */
@Getter
@NoArgsConstructor
public class ReviewCreateRequest {

    /** 별점 1~5. 생략 가능 */
    private Byte rating;

    /** QUIET / NORMAL / CROWDED. 생략 가능 */
    private String congestionReport;

    /** 한줄평 최대 60자 */
    private String content;

    /** 업로드 API 가 돌려준 URL, 최대 5장 */
    private List<String> imageUrls;
}
