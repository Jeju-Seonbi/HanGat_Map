package com.example.hangat.course.facts;

public record CandidateIdentity(
        String candidateId,
        Long placeId,
        String sourceCode,
        String sourcePlaceId
) {
    public CandidateIdentity {
        requireText(candidateId, "candidateId는 필수입니다.");
        rejectBlank(sourceCode, "sourceCode는 공백일 수 없습니다.");
        rejectBlank(sourcePlaceId, "sourcePlaceId는 공백일 수 없습니다.");
        boolean hasSourceCode = sourceCode != null;
        boolean hasSourcePlaceId = sourcePlaceId != null;
        if (hasSourceCode != hasSourcePlaceId) {
            throw new IllegalArgumentException(
                    "sourceCode와 sourcePlaceId는 함께 존재해야 합니다.");
        }
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rejectBlank(String value, String message) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
