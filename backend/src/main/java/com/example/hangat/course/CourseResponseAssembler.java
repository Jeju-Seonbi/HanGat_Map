package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiResultDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.Course;
import com.example.hangat.course.model.CourseItem;
import com.example.hangat.course.model.CourseResponseDto;
import com.example.hangat.course.model.CourseResponseDto.CongestionFactDto;
import com.example.hangat.course.model.CourseResponseDto.DayDto;
import com.example.hangat.course.model.CourseResponseDto.ItemDto;
import com.example.hangat.course.model.CourseResponseDto.ItemSource;
import com.example.hangat.course.model.CourseResponseDto.TourCategoryDto;
import com.example.hangat.course.model.CourseResponseDto.WeatherFactDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CourseResponseAssembler {

    public CourseResponseDto assemble(
            CourseAiInputDto input,
            CourseAiResultDto result,
            List<CourseCandidateDto> originalCandidates
    ) {
        return assemble(input, result, originalCandidates, null);
    }

    public CourseResponseDto assemble(
            CourseAiInputDto input,
            CourseAiResultDto result,
            List<CourseCandidateDto> originalCandidates,
            CoursePersistenceResult persistence
    ) {
        if (input == null || input.tripCondition() == null) {
            throw new IllegalArgumentException("AI 코스 입력이 필요합니다.");
        }
        if (result == null) {
            throw new IllegalArgumentException("AI 코스 생성 결과가 필요합니다.");
        }

        Map<String, CourseAiInputDto.CandidateFactDto> factsById = input.candidates().stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.identity().candidateId(),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        Map<String, CourseCandidateDto> originalsById = originalCandidates == null
                ? Map.of()
                : originalCandidates.stream()
                        .filter(candidate -> candidate != null && candidate.getPlace() != null)
                        .collect(Collectors.toMap(
                                candidate -> candidate.getPlace().getContentId(),
                                Function.identity(),
                                (first, second) -> first,
                                LinkedHashMap::new));

        List<DayDto> days = java.util.stream.IntStream.range(0, result.days().size())
                .mapToObj(dayIndex -> toDay(
                        dayIndex + 1,
                        result.days().get(dayIndex),
                        factsById,
                        originalsById,
                        input,
                        persistence))
                .toList();

        Course course = persistence == null ? null : persistence.course();
        return new CourseResponseDto(
                course == null ? null : course.getId(),
                result.contractVersion(),
                course == null ? null : course.getCourseType(),
                course == null ? null : course.getGenerationReason(),
                course == null ? null : course.getStatus(),
                input.tripCondition().startDate(),
                input.tripCondition().endDate(),
                course == null ? input.tripCondition().people() : course.getPeople(),
                course == null ? input.tripCondition().budgetTotal() : course.getBudgetTotal(),
                course == null ? input.tripCondition().transport() : course.getTransport(),
                days);
    }

    private DayDto toDay(
            int dayNo,
            CourseAiResultDto.DayDto day,
            Map<String, CourseAiInputDto.CandidateFactDto> factsById,
            Map<String, CourseCandidateDto> originalsById,
            CourseAiInputDto input,
            CoursePersistenceResult persistence
    ) {
        List<ItemDto> items = java.util.stream.IntStream.range(0, day.items().size())
                .mapToObj(itemIndex -> toItem(
                        itemIndex + 1,
                        day,
                        day.items().get(itemIndex),
                        factsById,
                        originalsById,
                        input,
                        persistence))
                .toList();
        return new DayDto(dayNo, day.date(), items);
    }

    private ItemDto toItem(
            int position,
            CourseAiResultDto.DayDto day,
            CourseAiResultDto.ItemDto item,
            Map<String, CourseAiInputDto.CandidateFactDto> factsById,
            Map<String, CourseCandidateDto> originalsById,
            CourseAiInputDto input,
            CoursePersistenceResult persistence
    ) {
        CourseAiInputDto.CandidateFactDto fact = factsById.get(item.candidateId());
        CourseCandidateDto original = originalsById.get(item.candidateId());
        if (fact == null || original == null) {
            throw new IllegalArgumentException(
                    "응답으로 변환할 수 없는 AI 후보 식별자입니다: " + item.candidateId());
        }

        CourseAiInputDto.TourCategoryDto category = fact.tourCategory();
        CourseItem persistedItem = persistence == null
                ? null
                : persistence.itemsByCandidateId().get(item.candidateId());
        return new ItemDto(
                persistedItem == null ? null : persistedItem.getId(),
                persistedItem == null ? null : persistedItem.getCourse().getId(),
                persistedItem == null ? null : persistedItem.getPlace().getId(),
                item.candidateId(),
                fact.name(),
                fact.address(),
                fact.latitude(),
                fact.longitude(),
                original.getPlace().getImageUrl(),
                category == null ? null : new TourCategoryDto(
                        category.category1(), category.category2(), category.category3()),
                fact.regionCode(),
                fact.preferenceType(),
                fact.confirmedStyleHints(),
                position,
                item.startTime(),
                persistedItem == null
                        ? (isFixedSchedule(day, item, fact, input)
                                ? ItemSource.USER_FIXED
                                : ItemSource.AI_RECOMMENDED)
                        : ItemSource.valueOf(persistedItem.getItemSource().name()),
                item.recommendationReason(),
                fact.congestion().stream()
                        .filter(congestion -> day.date().equals(congestion.date()))
                        .map(congestion -> new CongestionFactDto(
                                congestion.date(), congestion.rate(), congestion.level()))
                        .toList(),
                fact.weather() == null ? null : fact.weather().stream()
                        .filter(weather -> day.date().equals(weather.forecastDate()))
                        .map(weather -> new WeatherFactDto(
                                weather.forecastDate(), weather.forecastTime(),
                                weather.temperature(), weather.precipitationProbability(),
                                weather.precipitationTypeCode(), weather.skyConditionCode(),
                                weather.windSpeed(), weather.humidity()))
                        .toList());
    }

    private boolean isFixedSchedule(
            CourseAiResultDto.DayDto day,
            CourseAiResultDto.ItemDto item,
            CourseAiInputDto.CandidateFactDto candidate,
            CourseAiInputDto input
    ) {
        return input.userPreferences().requiredPlaces().stream()
                .anyMatch(required -> required.fixedDate() != null
                        && required.fixedDate().equals(day.date())
                        && (required.fixedTime() == null
                                || required.fixedTime().equals(item.startTime()))
                        && sameName(required.name(), candidate.name()));
    }

    private boolean sameName(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
