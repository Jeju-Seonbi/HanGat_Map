package com.example.hangat.course;

import com.example.hangat.course.model.GenerationReason;

/** Backend-owned metadata for one course generation run. */
public record CourseGenerationMetadata(
        GenerationReason generationReason,
        String algorithmVersion,
        String requestReference
) {
    public CourseGenerationMetadata {
        if (generationReason == null) {
            throw new IllegalArgumentException("AI 코스 생성 사유가 필요합니다.");
        }
    }
}
