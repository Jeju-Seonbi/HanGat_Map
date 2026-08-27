package com.example.hangat.course.ai;

public interface CourseAiProvider {

    CourseAiResultDto generate(CourseAiInputDto input);

    default CourseAiResultDto generateCorrection(
            CourseAiInputDto input,
            String validationFailureReason
    ) {
        return generate(input);
    }
}
