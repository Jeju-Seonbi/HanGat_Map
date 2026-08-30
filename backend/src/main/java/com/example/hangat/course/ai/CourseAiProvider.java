package com.example.hangat.course.ai;

public interface CourseAiProvider {

    CourseAiResultDto generate(CourseAiInputDto input);

    default CourseAiResultDto generateCorrection(
            CourseAiInputDto input,
            CourseAiResultDto previousResult,
            CourseAiValidationCode validationCode,
            String validationMessage
    ) {
        return generate(input);
    }
}
