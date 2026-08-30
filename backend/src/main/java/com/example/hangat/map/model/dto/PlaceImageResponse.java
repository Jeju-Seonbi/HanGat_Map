package com.example.hangat.map.model.dto;

import com.example.hangat.map.model.entity.PlaceImage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 장소 사진 한 장 - 상세 응답용. 출처표시 의무 때문에 attribution 을 같이 내린다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceImageResponse {

    private final String url;
    private final String thumbnailUrl;
    private final String caption;
    /** Type1(출처표시) / Type3(출처표시+변경금지) - 변경금지 사진은 화면에서 크롭·필터 금지 */
    private final String licenseCode;
    /** 화면에 그대로 표기할 출처 문구 */
    private final String attribution;
    private final boolean primary;

    public static PlaceImageResponse from(PlaceImage image) {
        return PlaceImageResponse.builder()
                .url(image.getImageUrl())
                .thumbnailUrl(image.getThumbnailUrl())
                .caption(image.getCaption())
                .licenseCode(image.getLicenseCode())
                .attribution(image.getAttribution())
                .primary(Boolean.TRUE.equals(image.getIsPrimary()))
                .build();
    }
}
