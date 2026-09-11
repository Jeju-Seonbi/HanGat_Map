package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.RequiredCandidateConstraintDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, provider-free fallback. It never relaxes hard constraints or invents places. */
@Component
public class DeterministicCourseFallback {
    private static final List<LocalTime> DEFAULT_TIMES = List.of(
            LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(18, 0));

    public CourseAiResultDto generate(CourseAiInputDto input) {
        if (input == null || input.trip() == null || input.trip().startDate() == null
                || input.trip().endDate() == null || input.candidates().isEmpty()) throw unavailable();
        List<LocalDate> dates = input.trip().startDate().datesUntil(input.trip().endDate().plusDays(1)).toList();
        if (input.candidates().size() < dates.size()) throw unavailable();
        boolean regenerate = input.generationMetadata() != null
                && input.generationMetadata().generationReason() == com.example.hangat.course.model.GenerationReason.USER_REGENERATE;
        if (regenerate && input.candidates().size() < 2) throw unavailable();

        Map<String, CandidateFactDto> byId = new LinkedHashMap<>();
        input.candidates().forEach(candidate -> byId.put(candidate.candidateId(), candidate));
        Map<LocalDate, List<CourseAiResultDto.ItemDto>> scheduled = new LinkedHashMap<>();
        dates.forEach(date -> scheduled.put(date, new ArrayList<>()));
        Set<String> used = new HashSet<>();

        for (RequiredCandidateConstraintDto required : input.hardConstraints().requiredCandidates()) {
            CandidateFactDto candidate = byId.get(required.candidateId());
            if (candidate == null || !used.add(required.candidateId())) throw unavailable();
            LocalDate date = required.fixedDate() == null ? leastLoaded(scheduled) : required.fixedDate();
            if (!scheduled.containsKey(date)) throw unavailable();
            LocalTime time = required.fixedTime() == null ? nextTime(scheduled.get(date)) : required.fixedTime();
            if (time == null || scheduled.get(date).stream().anyMatch(item -> time.equals(item.startTime()))) throw unavailable();
            scheduled.get(date).add(item(candidate, time, input.preferences().selectedStyleCodes()));
        }

        Comparator<CandidateFactDto> stableOrder = Comparator
                .comparingInt((CandidateFactDto candidate) -> styleMatches(candidate, input)).reversed()
                .thenComparingInt(this::congestionRank)
                .thenComparing(CandidateFactDto::candidateId,
                        regenerate ? Comparator.reverseOrder() : Comparator.naturalOrder());
        List<CandidateFactDto> ordinary = input.candidates().stream()
                .filter(candidate -> !used.contains(candidate.candidateId()))
                .sorted(stableOrder)
                .toList();
        int cursor = 0;
        // Populate empty dates first, then balance additional visits without consuming later days' candidates.
        while (cursor < ordinary.size()) {
            LocalDate date = scheduled.entrySet().stream()
                    .filter(entry -> entry.getValue().size() < 3 && nextTime(entry.getValue()) != null)
                    .min(Comparator.comparingInt((Map.Entry<LocalDate, List<CourseAiResultDto.ItemDto>> entry)
                            -> entry.getValue().size()).thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey).orElse(null);
            if (date == null) break;
            CandidateFactDto candidate = ordinary.get(cursor++);
            LocalTime time = nextTime(scheduled.get(date));
            if (time == null) break;
            used.add(candidate.candidateId());
            scheduled.get(date).add(item(candidate, time, input.preferences().selectedStyleCodes()));
        }
        if (scheduled.values().stream().anyMatch(List::isEmpty)) throw unavailable();
        return new CourseAiResultDto(input.contractVersion(), scheduled.entrySet().stream()
                .map(entry -> new CourseAiResultDto.DayDto(entry.getKey(), entry.getValue().stream()
                        .sorted(Comparator.comparing(CourseAiResultDto.ItemDto::startTime)).toList()))
                .toList());
    }

    private int congestionRank(CandidateFactDto candidate) {
        if (candidate.congestionFacts() == null || candidate.congestionFacts().isEmpty()) return 2;
        return candidate.congestionFacts().stream().map(CourseAiInputDto.CongestionFactDto::level)
                .filter(java.util.Objects::nonNull).mapToInt(level -> switch (level) {
                    case QUIET -> 0; case NORMAL -> 1; case CROWDED -> 3;
                }).min().orElse(2);
    }

    private int styleMatches(CandidateFactDto candidate, CourseAiInputDto input) {
        return (int) candidate.styleHintCodes().stream()
                .filter(input.preferences().selectedStyleCodes()::contains).count();
    }

    private CourseAiResultDto.ItemDto item(CandidateFactDto candidate, LocalTime time, List<String> styles) {
        String matched = candidate.styleHintCodes().stream().filter(styles::contains).findFirst().orElse(null);
        String reason = matched == null
                ? "확인된 후보와 날짜 조건을 기준으로 안정적으로 구성했어요."
                : "선택한 " + matched + " 스타일과 확인된 후보 정보를 기준으로 구성했어요.";
        return new CourseAiResultDto.ItemDto(candidate.candidateId(), time, reason);
    }

    private LocalDate leastLoaded(Map<LocalDate, List<CourseAiResultDto.ItemDto>> scheduled) {
        return scheduled.entrySet().stream().min(Comparator
                .comparingInt((Map.Entry<LocalDate, List<CourseAiResultDto.ItemDto>> entry) -> entry.getValue().size())
                .thenComparing(Map.Entry::getKey)).orElseThrow().getKey();
    }

    private LocalTime nextTime(List<CourseAiResultDto.ItemDto> items) {
        Set<LocalTime> used = new HashSet<>();
        items.forEach(item -> used.add(item.startTime()));
        return DEFAULT_TIMES.stream().filter(time -> !used.contains(time)).findFirst().orElse(null);
    }

    private CourseAiException unavailable() {
        return new CourseAiException(CourseAiFailureType.TEMPORARILY_UNAVAILABLE,
                "필수 조건을 유지한 대체 코스를 구성할 후보가 부족합니다.");
    }
}
