package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiResultDto;
import com.example.hangat.course.model.Course;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseItem;
import com.example.hangat.course.model.CourseItemSource;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.Place;
import com.example.hangat.course.model.PlaceCategory;
import com.example.hangat.course.model.PlaceSourceMapping;
import com.example.hangat.course.model.Region;
import com.example.hangat.course.model.TourPlaceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CoursePersistenceService {

    static final String KTO_SOURCE_CODE = "KTO";

    private final CourseRepository courseRepository;
    private final CourseItemRepository courseItemRepository;
    private final PlaceRepository placeRepository;
    private final PlaceSourceMappingRepository mappingRepository;
    private final RegionRepository regionRepository;
    private final PlaceCategoryRepository placeCategoryRepository;

    public CoursePersistenceService(
            CourseRepository courseRepository,
            CourseItemRepository courseItemRepository,
            PlaceRepository placeRepository,
            PlaceSourceMappingRepository mappingRepository,
            RegionRepository regionRepository,
            PlaceCategoryRepository placeCategoryRepository
    ) {
        this.courseRepository = courseRepository;
        this.courseItemRepository = courseItemRepository;
        this.placeRepository = placeRepository;
        this.mappingRepository = mappingRepository;
        this.regionRepository = regionRepository;
        this.placeCategoryRepository = placeCategoryRepository;
    }

    @Transactional
    public CoursePersistenceResult persist(
            CourseRequestDto request,
            CourseAiInputDto input,
            CourseAiResultDto result,
            List<CourseCandidateDto> originalCandidates
    ) {
        if (request == null || input == null || result == null) {
            throw new IllegalArgumentException("저장할 코스 생성 결과가 필요합니다.");
        }
        if (result.days().isEmpty()
                || result.days().stream().allMatch(day -> day.items().isEmpty())) {
            throw new IllegalArgumentException("방문 일정이 없는 코스는 저장할 수 없습니다.");
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

        Course course = courseRepository.save(Course.ready(
                request.getStartDate(),
                request.getEndDate(),
                request.getPeople(),
                request.getBudgetTotal(),
                request.getTransport(),
                input.generationMetadata() == null
                        ? null
                        : input.generationMetadata().generationReason(),
                input.generationMetadata() == null
                        ? null
                        : input.generationMetadata().algorithmVersion()));

        Map<String, CourseItem> itemsByCandidateId = new LinkedHashMap<>();
        Map<String, String> categoryNamesByCandidateId = new LinkedHashMap<>();
        for (int dayIndex = 0; dayIndex < result.days().size(); dayIndex++) {
            CourseAiResultDto.DayDto day = result.days().get(dayIndex);
            int dayNo = dayIndex + 1;

            for (int itemIndex = 0; itemIndex < day.items().size(); itemIndex++) {
                CourseAiResultDto.ItemDto resultItem = day.items().get(itemIndex);
                CourseAiInputDto.CandidateFactDto fact = factsById.get(resultItem.candidateId());
                CourseCandidateDto original = originalsById.get(resultItem.candidateId());
                if (fact == null || original == null) {
                    throw new IllegalStateException(
                            "저장할 수 없는 AI 후보 식별자입니다: " + resultItem.candidateId());
                }

                Place place = resolvePlace(fact, original.getPlace());
                CourseItem item = courseItemRepository.save(CourseItem.generated(
                        course,
                        place,
                        dayNo,
                        itemIndex + 1,
                        day.date(),
                        resultItem.startTime(),
                        resolveItemSource(input, fact, day, resultItem),
                        resultItem.recommendationReason()));
                itemsByCandidateId.put(resultItem.candidateId(), item);
                categoryNamesByCandidateId.put(
                        resultItem.candidateId(),
                        place.getPrimaryCategory().getName());
            }
        }

        return new CoursePersistenceResult(
                course,
                itemsByCandidateId,
                categoryNamesByCandidateId);
    }

    private Place resolvePlace(
            CourseAiInputDto.CandidateFactDto fact,
            TourPlaceDto original
    ) {
        CourseAiInputDto.PlaceIdentityDto identity = fact.identity();
        if (identity.placeId() != null) {
            return placeRepository.findById(identity.placeId())
                    .orElseThrow(() -> new IllegalStateException(
                            "내부 장소를 찾을 수 없습니다: " + identity.placeId()));
        }

        String sourceCode = firstNonBlank(identity.sourceCode(), KTO_SOURCE_CODE)
                .trim().toUpperCase(Locale.ROOT);
        String sourcePlaceId = firstNonBlank(identity.sourcePlaceId(), original.getContentId());
        if (sourcePlaceId == null || sourcePlaceId.isBlank()) {
            throw new IllegalStateException("외부 장소 식별자가 없는 후보는 저장할 수 없습니다.");
        }

        return mappingRepository.findBySourceCodeAndSourcePlaceId(sourceCode, sourcePlaceId)
                .map(PlaceSourceMapping::getPlace)
                .orElseGet(() -> createMappedPlace(fact, sourceCode, sourcePlaceId));
    }

    private Place createMappedPlace(
            CourseAiInputDto.CandidateFactDto fact,
            String sourceCode,
            String sourcePlaceId
    ) {
        if (!KTO_SOURCE_CODE.equals(sourceCode)) {
            throw new IllegalStateException(
                    "기존 매핑이 없는 외부 출처 장소는 생성할 수 없습니다: " + sourceCode);
        }
        if (fact.name() == null || fact.name().isBlank()) {
            throw new IllegalStateException("장소명이 없는 KTO 후보는 저장할 수 없습니다.");
        }
        if (fact.regionCode() == null || "UNKNOWN".equalsIgnoreCase(fact.regionCode())) {
            throw new IllegalStateException("권역을 확인할 수 없는 KTO 후보는 저장할 수 없습니다.");
        }
        if (fact.tourCategory() == null) {
            throw new IllegalStateException("관광 카테고리가 없는 KTO 후보는 저장할 수 없습니다.");
        }

        Region region = regionRepository.findByCodeIgnoreCaseAndActiveTrue(fact.regionCode())
                .orElseThrow(() -> new IllegalStateException(
                        "등록된 권역 코드를 찾을 수 없습니다: " + fact.regionCode()));
        String categoryCode = KtoPlaceCategoryResolver.resolve(
                fact.tourCategory().category1(),
                fact.tourCategory().category3());
        PlaceCategory category = placeCategoryRepository
                .findByCodeIgnoreCaseAndActiveTrue(categoryCode)
                .orElseThrow(() -> new IllegalStateException(
                        "등록된 장소 카테고리를 찾을 수 없습니다: " + categoryCode));

        Place place = placeRepository.save(Place.fromExternalCandidate(
                region,
                category,
                fact.name(),
                normalizeName(fact.name()),
                fact.address(),
                fact.latitude(),
                fact.longitude()));
        mappingRepository.save(PlaceSourceMapping.active(
                place,
                sourceCode,
                sourcePlaceId));
        return place;
    }

    private CourseItemSource resolveItemSource(
            CourseAiInputDto input,
            CourseAiInputDto.CandidateFactDto fact,
            CourseAiResultDto.DayDto day,
            CourseAiResultDto.ItemDto item
    ) {
        boolean fixed = input.userPreferences().requiredPlaces().stream()
                .anyMatch(required -> required.fixedDate() != null
                        && required.fixedDate().equals(day.date())
                        && (required.fixedTime() == null
                                || required.fixedTime().equals(item.startTime()))
                        && normalizeName(required.name()).equals(normalizeName(fact.name())));
        return fixed ? CourseItemSource.USER_FIXED : CourseItemSource.AI_RECOMMENDED;
    }

    private String normalizeName(String value) {
        return value == null
                ? ""
                : value.replaceAll("[\\s\\p{P}\\p{S}]+", "")
                        .toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
