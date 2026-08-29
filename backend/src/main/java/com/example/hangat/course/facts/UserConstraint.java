package com.example.hangat.course.facts;

import com.example.hangat.course.model.PreferenceType;

import java.time.LocalDate;
import java.time.LocalTime;

public record UserConstraint(
        PreferenceType preferenceType,
        LocalDate fixedDate,
        LocalTime fixedTime
) {
    public UserConstraint {
        if (preferenceType == PreferenceType.AVOID) {
            throw new IllegalArgumentException("AVOID 장소는 CourseCandidate로 만들 수 없습니다.");
        }
        if (fixedTime != null && fixedDate == null) {
            throw new IllegalArgumentException("fixedTime은 fixedDate 없이 존재할 수 없습니다.");
        }
        if ((fixedDate != null || fixedTime != null) && preferenceType != PreferenceType.WANT) {
            throw new IllegalArgumentException("고정 일정은 WANT 후보에만 지정할 수 있습니다.");
        }
    }

    public static UserConstraint none() {
        return new UserConstraint(null, null, null);
    }

    public static UserConstraint want(LocalDate fixedDate, LocalTime fixedTime) {
        return new UserConstraint(PreferenceType.WANT, fixedDate, fixedTime);
    }
}
