package com.example.hangat.course.travel;

import com.example.hangat.course.model.Transport;

import java.math.BigDecimal;
import java.time.Instant;

public record CourseTravelLegDto(
        String fromCandidateId,
        String fromTitle,
        String toCandidateId,
        String toTitle,
        BigDecimal straightDistanceKm,
        DistanceCalculationMethod straightDistanceMethod,
        BigDecimal routeDistanceKm,
        Integer durationMinutes,
        Transport transport,
        String routeSourceCode,
        Instant routeCalculatedAt
) {

    public boolean hasRouteData() {
        return routeDistanceKm != null && durationMinutes != null
                && routeSourceCode != null && routeCalculatedAt != null;
    }
}
