package com.example.hangat.course.ai;

public class CourseAiException extends RuntimeException {

    private final CourseAiFailureType failureType;

    public CourseAiException(CourseAiFailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public CourseAiException(
            CourseAiFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
    }

    public CourseAiFailureType getFailureType() {
        return failureType;
    }
}
