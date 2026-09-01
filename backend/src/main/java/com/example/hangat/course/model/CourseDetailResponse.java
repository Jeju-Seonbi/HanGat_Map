package com.example.hangat.course.model;

import com.example.hangat.course.model.enums.CourseItemSource;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.model.enums.CourseType;
import com.example.hangat.course.model.enums.Transport;
import com.example.hangat.map.model.enums.CongestionLevel;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 저장된 코스 한 건의 상세 - 메인 추천 카드·저장 코스 목록에서 코스를 열 때의 응답.
 *
 * <p><b>생성 응답({@code CourseResponseDto})과 다른 DTO인 이유</b>: 그쪽은 생성 시점 산출물
 * (후보 근거·프롬프트 팩트·예산 계산 내역)을 함께 나르는 무거운 계약이라 저장된 행만으로는
 * 채울 수 없다. 여기는 <b>화면이 그리는 데 필요한 것만</b> 담는다.
 *
 * <p><b>혼잡은 두 값을 함께 준다</b>(명세서 21.0/25.0): {@code congestionRate}는 지금 예보,
 * {@code plannedCongestionRate}는 저장 시점 스냅숏이다. 예보는 매일 갱신되므로 재열람 시
 * "그때는 이랬는데 지금은 이렇다"를 화면이 함께 보여줄 수 있어야 한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseDetailResponse(
        Long id,
        CourseType courseType,
        CourseStatus status,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int durationDays,
        String durationText,
        Short people,
        Integer budgetTotal,
        Transport transport,
        Integer estimatedCostMin,
        Integer estimatedCostMax,
        BigDecimal averageCongestionRate,
        CongestionLevel congestionLevel,
        String congestionLabel,
        /** 이 사용자가 스왑·이름수정·삭제를 할 수 있는지 - 화면이 버튼 노출을 결정한다. */
        boolean editable,
        List<DayDto> days
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DayDto(
            int dayNo,
            LocalDate visitDate,
            List<ItemDto> items
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemDto(
            Long id,
            Long placeId,
            String placeName,
            String categoryName,
            String regionName,
            String imageUrl,
            Double latitude,
            Double longitude,
            int dayNo,
            int position,
            LocalDate visitDate,
            LocalTime startTime,
            LocalTime endTime,
            CourseItemSource itemSource,
            Integer inboundDistanceM,
            Integer inboundTravelMinutes,
            /** 지금 예보. 커버 밖이면 null - '혼잡 정보 없음'으로 표시한다. */
            Double congestionRate,
            CongestionLevel congestionLevel,
            String congestionLabel,
            /** 저장(생성) 시점 스냅숏. 지금 예보와 다르면 화면이 변화를 알려줄 수 있다. */
            Double plannedCongestionRate,
            String recommendationReasonCode,
            String recommendationReason,
            /** 스왑으로 바뀐 일정에만 값이 있다. */
            String replacedFromPlaceName
    ) {
    }
}
