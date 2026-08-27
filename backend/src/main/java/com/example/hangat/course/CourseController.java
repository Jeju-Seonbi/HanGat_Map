package com.example.hangat.course;

import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.CourseResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping("/courses")
    public CourseResponseDto createCourse(@RequestBody CourseRequestDto request) {
        return courseService.createCourse(request);
    }
}
