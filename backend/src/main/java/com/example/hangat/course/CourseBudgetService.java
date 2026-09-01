package com.example.hangat.course;

import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.repository.CourseItemCostRepository;
import com.example.hangat.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비용 원장 조회, 집계 및 {@code courses} 범위 캐시 갱신의 트랜잭션 경계다. */
@Service
@RequiredArgsConstructor
public class CourseBudgetService {

    private final CourseRepository courseRepository;
    private final CourseItemCostRepository costRepository;
    private final CourseBudgetCalculator calculator;

    @Transactional
    public CourseBudgetCalculation calculateAndCache(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalStateException(
                        "예산을 계산할 코스를 찾을 수 없습니다: " + courseId));
        CourseBudgetCalculation calculation = calculator.calculate(
                course.getBudgetTotal(), costRepository.findByCourseId(courseId));
        course.updateAggregates(
                calculation.totalExpectedMin(),
                calculation.totalExpectedMax(),
                course.getAverageCongestionRate());
        return calculation;
    }
}
