package com.example.hangat.course;

import com.example.hangat.course.model.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {
    List<CourseItem> findAllByCourseIdOrderByDayNoAscPositionAsc(Long courseId);
}
