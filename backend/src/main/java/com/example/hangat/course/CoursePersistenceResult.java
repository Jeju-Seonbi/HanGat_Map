package com.example.hangat.course;

import com.example.hangat.course.model.Course;
import com.example.hangat.course.model.CourseItem;

import java.util.Map;

public record CoursePersistenceResult(
        Course course,
        Map<String, CourseItem> itemsByCandidateId,
        Map<String, String> categoryNamesByCandidateId
) {
    public CoursePersistenceResult {
        itemsByCandidateId = itemsByCandidateId == null
                ? Map.of()
                : Map.copyOf(itemsByCandidateId);
        categoryNamesByCandidateId = categoryNamesByCandidateId == null
                ? Map.of()
                : Map.copyOf(categoryNamesByCandidateId);
    }

    public CoursePersistenceResult(
            Course course,
            Map<String, CourseItem> itemsByCandidateId
    ) {
        this(course, itemsByCandidateId, Map.of());
    }
}
