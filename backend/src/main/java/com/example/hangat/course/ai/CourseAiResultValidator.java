package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceConstraintDto;
import com.example.hangat.course.ai.CourseAiResultDto.DayDto;
import com.example.hangat.course.ai.CourseAiResultDto.ItemDto;
import com.example.hangat.course.model.PreferenceType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CourseAiResultValidator {

    public void validate(CourseAiInputDto input, CourseAiResultDto result) {
        if (input == null || input.tripCondition() == null || result == null) {
            fail("AI 코스 입력 또는 결과가 없습니다.");
        }
        if (!input.contractVersion().equals(result.contractVersion())) {
            fail("AI 코스 결과 계약 버전이 입력과 다릅니다.");
        }

        Map<String, CandidateFactDto> candidatesById = candidateMap(input.candidates());
        Map<String, ScheduledItem> scheduledByCandidate = new HashMap<>();
        Set<LocalDate> scheduledDates = new HashSet<>();

        for (DayDto day : result.days()) {
            validateDay(input, day, scheduledDates);
            for (ItemDto item : day.items()) {
                validateItem(item, candidatesById, scheduledByCandidate, day.date());
            }
        }

        validateForbidden(input.userPreferences().forbiddenPlaces(), candidatesById, scheduledByCandidate);
        validateRequired(input, candidatesById, scheduledByCandidate);
    }

    private Map<String, CandidateFactDto> candidateMap(List<CandidateFactDto> candidates) {
        Map<String, CandidateFactDto> result = new HashMap<>();
        for (CandidateFactDto candidate : candidates) {
            if (candidate == null || candidate.identity() == null
                    || isBlank(candidate.identity().candidateId())) {
                fail("AI 코스 후보의 candidateId가 유효하지 않습니다.");
            }
            if (result.put(candidate.identity().candidateId(), candidate) != null) {
                fail("AI 코스 입력에 중복 candidateId가 있습니다.");
            }
        }
        return result;
    }

    private void validateDay(
            CourseAiInputDto input,
            DayDto day,
            Set<LocalDate> scheduledDates
    ) {
        if (day == null || day.date() == null) {
            fail("AI 코스 결과의 방문일이 없습니다.");
        }
        LocalDate startDate = input.tripCondition().startDate();
        LocalDate endDate = input.tripCondition().endDate();
        if (day.date().isBefore(startDate) || day.date().isAfter(endDate)) {
            fail("AI 코스 결과에 여행기간 밖 날짜가 있습니다.");
        }
        if (!scheduledDates.add(day.date())) {
            fail("AI 코스 결과에 동일 날짜가 중복되었습니다.");
        }
    }

    private void validateItem(
            ItemDto item,
            Map<String, CandidateFactDto> candidatesById,
            Map<String, ScheduledItem> scheduledByCandidate,
            LocalDate date
    ) {
        if (item == null || isBlank(item.candidateId())) {
            fail("AI 코스 결과의 candidateId가 유효하지 않습니다.");
        }
        if (!candidatesById.containsKey(item.candidateId())) {
            fail("AI 코스 결과에 입력 후보가 아닌 candidateId가 있습니다.");
        }
        if (item.startTime() == null) {
            fail("AI 코스 결과의 방문 시작 시간이 없습니다.");
        }
        if (isBlank(item.recommendationReason())) {
            fail("AI 코스 결과의 추천 이유가 없습니다.");
        }
        if (scheduledByCandidate.put(item.candidateId(), new ScheduledItem(date, item)) != null) {
            fail("AI 코스 결과에 같은 candidateId가 중복 배치되었습니다.");
        }
    }

    private void validateForbidden(
            List<PlaceConstraintDto> forbidden,
            Map<String, CandidateFactDto> candidatesById,
            Map<String, ScheduledItem> scheduledByCandidate
    ) {
        for (PlaceConstraintDto constraint : forbidden) {
            CandidateFactDto matched = findCandidate(constraint, candidatesById);
            if (matched != null && scheduledByCandidate.containsKey(matched.identity().candidateId())) {
                fail("AI 코스 결과에 AVOID 장소가 포함되었습니다.");
            }
        }
    }

    private void validateRequired(
            CourseAiInputDto input,
            Map<String, CandidateFactDto> candidatesById,
            Map<String, ScheduledItem> scheduledByCandidate
    ) {
        Set<String> requiredCandidateIds = new HashSet<>();
        for (CandidateFactDto candidate : candidatesById.values()) {
            if (candidate.preferenceType() == PreferenceType.WANT) {
                requiredCandidateIds.add(candidate.identity().candidateId());
            }
        }

        for (PlaceConstraintDto constraint : input.userPreferences().requiredPlaces()) {
            CandidateFactDto candidate = findCandidate(constraint, candidatesById);
            if (candidate == null) {
                fail("WANT 장소와 일치하는 AI 코스 후보가 없습니다.");
            }
            requiredCandidateIds.add(candidate.identity().candidateId());
            ScheduledItem scheduled = scheduledByCandidate.get(candidate.identity().candidateId());
            if (scheduled == null) {
                fail("AI 코스 결과에서 WANT 장소가 누락되었습니다.");
            }
            if (constraint.fixedDate() != null && !constraint.fixedDate().equals(scheduled.date())) {
                fail("AI 코스 결과가 WANT 장소의 fixedDate를 변경했습니다.");
            }
            if (constraint.fixedTime() != null
                    && !constraint.fixedTime().equals(scheduled.item().startTime())) {
                fail("AI 코스 결과가 WANT 장소의 fixedTime을 변경했습니다.");
            }
        }

        for (String requiredCandidateId : requiredCandidateIds) {
            if (!scheduledByCandidate.containsKey(requiredCandidateId)) {
                fail("AI 코스 결과에서 WANT 장소가 누락되었습니다.");
            }
        }
    }

    private CandidateFactDto findCandidate(
            PlaceConstraintDto constraint,
            Map<String, CandidateFactDto> candidatesById
    ) {
        if (constraint == null) {
            return null;
        }
        if (constraint.identity() != null && !isBlank(constraint.identity().candidateId())) {
            return candidatesById.get(constraint.identity().candidateId());
        }
        String normalizedName = normalize(constraint.name());
        if (normalizedName.isEmpty()) {
            return null;
        }
        CandidateFactDto match = null;
        for (CandidateFactDto candidate : candidatesById.values()) {
            if (!normalizedName.equals(normalize(candidate.name()))) {
                continue;
            }
            if (match != null) {
                fail("장소명으로 WANT/AVOID 후보를 하나로 식별할 수 없습니다.");
            }
            match = candidate;
        }
        return match;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void fail(String message) {
        throw new CourseAiException(CourseAiFailureType.VALIDATION_ERROR, message);
    }

    private record ScheduledItem(LocalDate date, ItemDto item) {
    }
}
