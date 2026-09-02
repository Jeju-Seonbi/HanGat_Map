package com.example.hangat.course.ai;

public class CourseAiValidationException extends CourseAiException {

    private final CourseAiValidationCode code;

    public CourseAiValidationException(CourseAiValidationCode code, String message) {
        super(CourseAiFailureType.VALIDATION_ERROR, message);
        this.code = code;
    }

    public CourseAiValidationCode getCode() {
        return code;
    }
}
