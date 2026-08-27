package com.example.hangat.course;

import com.example.hangat.course.model.Course;
import com.example.hangat.course.model.CourseItem;

import java.util.Map;

public record CoursePersistenceResult(
        Course course,
        Map<String, CourseItem> itemsByCandidateId
) {
    public CoursePersistenceResult {
        itemsByCandidateId = itemsByCandidateId == null
                ? Map.of()
                : Map.copyOf(itemsByCandidateId);
    }
}
