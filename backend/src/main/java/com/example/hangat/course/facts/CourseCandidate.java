package com.example.hangat.course.facts;

import java.util.List;
import java.util.Objects;

public record CourseCandidate(
        CandidateIdentity identity,
        PlaceFact place,
        UserConstraint userConstraint,
        String regionCode,
        List<ExternalClassificationFact> externalClassifications,
        InternalPlaceCategory internalPlaceCategory,
        List<StyleHint> styleHints,
        List<CongestionFact> congestionFacts,
        String weatherFactSetId
) {
    public CourseCandidate {
        Objects.requireNonNull(identity, "후보 identity는 필수입니다.");
        Objects.requireNonNull(place, "후보 장소 사실은 필수입니다.");
        Objects.requireNonNull(userConstraint, "사용자 제약은 필수입니다.");
        externalClassifications = immutableList(externalClassifications);
        styleHints = immutableList(styleHints);
        congestionFacts = immutableList(congestionFacts);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
