package com.example.hangat.course.facts;

import com.example.hangat.course.model.Transport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TravelFact(
        String fromCandidateId,
        String toCandidateId,
        BigDecimal straightDistanceMeters,
        String straightDistanceMethod,
        BigDecimal routeDistanceMeters,
        Integer travelMinutes,
        Transport transport,
        String routeSourceCode,
        Instant routeCalculatedAt
) {
    public TravelFact {
        requireText(fromCandidateId, "출발 candidateId는 필수입니다.");
        requireText(toCandidateId, "도착 candidateId는 필수입니다.");
        if (fromCandidateId.equals(toCandidateId)) {
            throw new IllegalArgumentException("출발·도착 candidateId는 달라야 합니다.");
        }
        Objects.requireNonNull(straightDistanceMeters, "직선거리는 필수입니다.");
        requireText(straightDistanceMethod, "직선거리 계산 방식은 필수입니다.");
        Objects.requireNonNull(transport, "이동수단은 필수입니다.");
        requireNonNegative(straightDistanceMeters, "직선거리");
        requireNonNegative(routeDistanceMeters, "경로거리");
        if (travelMinutes != null && travelMinutes < 0) {
            throw new IllegalArgumentException("이동시간은 음수일 수 없습니다.");
        }
        if (routeSourceCode != null && routeSourceCode.isBlank()) {
            throw new IllegalArgumentException("경로 sourceCode는 공백일 수 없습니다.");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(label + "는 음수일 수 없습니다.");
        }
    }
}
