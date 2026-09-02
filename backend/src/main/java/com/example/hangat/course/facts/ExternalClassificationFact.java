package com.example.hangat.course.facts;

public record ExternalClassificationFact(
        String sourceCode,
        String level1Code,
        String level2Code,
        String level3Code,
        String categoryName
) {
    public ExternalClassificationFact {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("외부 분류의 sourceCode는 필수입니다.");
        }
    }
}
