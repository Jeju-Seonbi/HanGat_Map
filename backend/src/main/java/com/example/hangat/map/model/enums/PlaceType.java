package com.example.hangat.map.model.enums;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;
import java.util.Optional;

/**
 * 프론트 {@code ?type=} 필터 값 - 설계서 §2.1 매핑표를 <b>(카테고리 코드, 착한가격 여부) 두 칸짜리 조건표</b>로 정규화한다.
 *
 * <p>착한가격업소는 카테고리가 아니라 {@code places.is_good_price} 플래그다(§2.1). 그래서 type별 조건이 균일하지 않은데,
 * 축이 다른 게 아니라 <i>같은 2축 중 어느 것을 거느냐</i>가 다를 뿐이다. {@code null} = 그 축에 조건을 걸지 않음.
 * type이 늘어도 이 표에 한 줄만 추가하면 되고 서비스·리포지토리는 안 건드린다.
 *
 * <p>카테고리 코드를 자바 enum이 아닌 String으로 든 이유는 §9.1 - 카테고리 값 추가는 테이블 행 추가로 끝나야 한다.
 */
@Getter
@AllArgsConstructor
public enum PlaceType {

    SPOT("TOURIST", null),
    /** 착한가격업소. 카테고리를 따지지 않는다(식당·카페 어디든 지정될 수 있다). */
    FOOD(null, Boolean.TRUE),
    /** 일반 식당 = FOOD 카테고리 중 착한가격업소가 아닌 곳. 이름과 조건이 어긋나 보이지만 프론트 계약이다. */
    DINE("FOOD", Boolean.FALSE),
    CAFE("CAFE", null),
    STAY("LODGING", null),
    CVS("CONVENIENCE", null),
    MART("MART", null);

    /** {@code place_categories.code}. null이면 카테고리 무관. */
    private final String categoryCode;

    /** {@code places.is_good_price}. null이면 착한가격 여부 무관. */
    private final Boolean goodPrice;

    /**
     * 쿼리 파라미터 → 상수. 생략과 {@code ?type=}(빈 문자열)은 둘 다 '전체'라 {@link Optional#empty()},
     * 모르는 값은 {@code REQUEST_ERROR(3000)} → 400이다. 조용히 전체를 돌려주면 프론트 오타가
     * '지도가 좀 이상한데?'로만 나타나 원인 추적이 오래 걸린다.
     *
     * <p>컨트롤러에서 enum으로 직접 바인딩하지 않고 문자열로 받아 여기서 변환하는 이유:
     * Spring 기본 변환기는 소문자 {@code spot}을 못 읽고, 그 실패는 GlobalExceptionHandler가 잡지 않아
     * 응답이 BaseResponse 봉투를 벗어난다(§8.1).
     */
    public static Optional<PlaceType> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
    }
}
