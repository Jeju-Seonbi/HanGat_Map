package com.example.hangat.course.model;

import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.entity.CourseItem;
import com.example.hangat.map.model.enums.CongestionLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 메인 추천 코스 카드 한 장 (MAIN_002).
 *
 * <p>estimatedCostMin/Max가 null이면 프론트가 "요금 확인 필요"로 표시한다 -
 * 입장료 실측치가 없는 동안 추정치를 지어내지 않는다(정직성 원칙).
 */
public record MainCourseResponse(
        Long courseId,
        String title,
        String regionLabel,
        int durationDays,
        String durationText,
        LocalDate startDate,
        LocalDate endDate,
        Short people,
        BigDecimal averageCongestionRate,
        CongestionLevel level,
        String levelLabel,
        String imageUrl,
        int placeCount,
        List<String> highlightNames,
        Integer estimatedCostMin,
        Integer estimatedCostMax
) {

    private static final int HIGHLIGHT_COUNT = 3;

    public static MainCourseResponse of(Course course, List<CourseItem> items) {
        // ChronoUnit인 이유: 연말 경계(12/31→1/1)에서 dayOfYear 뺄셈은 음수가 된다
        int days = (int) ChronoUnit.DAYS.between(course.getStartDate(), course.getEndDate()) + 1;
        BigDecimal avg = course.getAverageCongestionRate();
        return new MainCourseResponse(
                course.getId(),
                course.getTitle(),
                items.isEmpty() ? null : items.get(0).getPlace().getRegion().getName(),
                days,
                (days - 1) + "박 " + days + "일",
                course.getStartDate(),
                course.getEndDate(),
                course.getPeople(),
                avg,
                avg == null ? null : CongestionLevel.from(avg),
                avg == null ? null : CongestionLevel.from(avg).label(),
                items.stream()
                        .map(item -> item.getPlace().getImageUrl())
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null),
                items.size(),
                items.stream()
                        .map(item -> item.getPlace().getName())
                        .limit(HIGHLIGHT_COUNT)
                        .toList(),
                course.getEstimatedCostMin(),
                course.getEstimatedCostMax()
        );
    }
}
