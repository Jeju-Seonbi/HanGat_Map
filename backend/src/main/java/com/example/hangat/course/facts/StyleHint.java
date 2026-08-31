package com.example.hangat.course.facts;

public record StyleHint(
        String styleCode,
        String evidenceSource,
        String evidenceValue
) {
    public StyleHint {
        requireText(styleCode, "스타일 코드는 필수입니다.");
        requireText(evidenceSource, "스타일 근거 출처는 필수입니다.");
        requireText(evidenceValue, "스타일 근거 값은 필수입니다.");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
