package com.example.hangat.course.model;

import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.entity.CourseItem;
import com.example.hangat.course.model.enums.CourseType;
import com.example.hangat.course.model.enums.Transport;
import com.example.hangat.map.model.enums.CongestionLevel;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 코스 카드 한 장 - <b>메인 추천(MAIN_002)과 저장 코스 목록(MY_001)이 같은 DTO를 쓴다.</b>
 * 화면상 같은 카드라 계약을 나누면 프론트가 렌더러를 두 벌 만들게 된다.
 *
 * <p>비용이 null이면 화면은 "요금 확인 필요"로 표시한다 - 입장료 실측치가 없는 동안
 * 추정치를 지어내지 않는다(정직성 원칙).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CourseSummaryResponse(
        Long id,
        CourseType courseType,
        String title,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        int durationDays,
        String durationText,
        Short people,
        Transport transport,
        BigDecimal averageCongestionRate,
        CongestionLevel congestionLevel,
        String congestionLabel,
        Integer estimatedCostMin,
        Integer estimatedCostMax,
        String imageUrl,
        int placeCount,
        List<String> highlightNames,
        /** 저장 코스 목록의 정렬 기준. 저장하지 않은 코스는 null. */
        LocalDateTime savedAt
) {

    private static final int HIGHLIGHT_COUNT = 3;

    public static CourseSummaryResponse of(Course course, List<CourseItem> items) {
        CourseDuration duration = CourseDuration.between(course.getStartDate(), course.getEndDate());
        BigDecimal avg = course.getAverageCongestionRate();
        return new CourseSummaryResponse(
                course.getId(),
                course.getCourseType(),
                course.getTitle(),
                items.isEmpty() ? null : items.get(0).getPlace().getRegion().getName(),
                course.getStartDate(),
                course.getEndDate(),
                duration.days(),
                duration.text(),
                course.getPeople(),
                course.getTransport(),
                avg,
                avg == null ? null : CongestionLevel.from(avg),
                avg == null ? null : CongestionLevel.from(avg).label(),
                course.getEstimatedCostMin(),
                course.getEstimatedCostMax(),
                items.stream()
                        .map(item -> item.getPlace().getImageUrl())
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null),
                items.size(),
                items.stream()
                        .map(item -> item.getPlace().getName())
                        .limit(HIGHLIGHT_COUNT)
                        .toList(),
                course.getSavedAt());
    }
}
