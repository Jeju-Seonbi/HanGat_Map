package com.example.hangat.map.detail;

import com.example.hangat.map.model.dto.PlaceDetailResponse;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상세 응답의 입장료 노출 검증 (MAP-07).
 *
 * <p>★ 무료 배지가 급소다. 조건부 무료("무료※ 단, 유료 특별전시 제외")를 무료로 처리하면
 * 유료 전시를 무료로 안내하게 된다.
 */
class PlaceDetailResponseFeeTest {

    private Place place(String useFee) {
        Place p = Place.builder()
                .region(Region.builder().code("WEST").name("서부").displayOrder((byte) 1).build())
                .primaryCategory(PlaceCategory.builder().code("TOURIST").name("관광지").build())
                .name("감귤박물관").normalizedName("감귤박물관")
                .build();
        p.updateDetail(null, null, null, null, useFee);
        return p;
    }

    @Test
    void 입장료_원문이_그대로_나간다() {
        String 실측 = "[개인]- 일반 1,500원- 청소년 1,000원-어린이 800원";

        PlaceDetailResponse res = PlaceDetailResponse.from(place(실측), null, null);

        assertThat(res.getUseFeeText()).isEqualTo(실측);
        assertThat(res.isFree()).isFalse();
    }

    @Test
    void 무료면_배지가_켜진다() {
        PlaceDetailResponse res = PlaceDetailResponse.from(place("무료"), null, null);

        assertThat(res.isFree()).isTrue();
    }

    @Test
    void 조건부_무료는_배지를_켜지_않는다() {
        // 실측: 국립제주박물관 - "무료※ 단, 유료 특별전시 제외"
        PlaceDetailResponse res = PlaceDetailResponse.from(place("무료※ 단, 유료 특별전시 제외"), null, null);

        assertThat(res.isFree()).isFalse();
        // 원문은 그대로 내려 화면이 조건을 보여줄 수 있게 한다
        assertThat(res.getUseFeeText()).contains("유료 특별전시 제외");
    }

    @Test
    void 입장료가_없으면_null이고_무료도_아니다() {
        // 관광지 563곳에는 usefee 필드 자체가 없다 - '무료'가 아니라 '모름'이다
        PlaceDetailResponse res = PlaceDetailResponse.from(place(null), null, null);

        assertThat(res.getUseFeeText()).isNull();
        assertThat(res.isFree()).isFalse();
    }
}
