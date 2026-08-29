package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiResultDto;
import com.example.hangat.course.model.Course;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseItem;
import com.example.hangat.course.model.CourseItemSource;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.CourseResponseDto;
import com.example.hangat.course.model.CourseStatus;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.model.PlaceCategory;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.Region;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.model.Transport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CoursePersistenceService.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoursePersistenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private CoursePersistenceService persistenceService;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseItemRepository courseItemRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PlaceSourceMappingRepository mappingRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private PlaceCategoryRepository categoryRepository;

    @BeforeEach
    void setUpReferences() {
        courseItemRepository.deleteAll();
        mappingRepository.deleteAll();
        courseRepository.deleteAll();
        placeRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();

        regionRepository.save(Region.reference("EAST", "동부", 1));
        categoryRepository.save(PlaceCategory.reference("TOURIST", "관광지", 1));
    }

    @Test
    void savesReadyCourseMappedPlaceAndItemsWithActualDatabaseIds() throws Exception {
        CourseCandidateDto candidate = candidate(
                "KTO-1001", "성산일출봉", "제주특별자치도 서귀포시 성산읍", "A01");
        CourseAiInputDto input = input(List.of(fact(candidate, PreferenceType.WANT)), true);
        CourseAiResultDto result = result("KTO-1001", "2026-08-27", "09:00");

        CoursePersistenceResult persisted = persistenceService.persist(
                request(), input, result, List.of(candidate));

        assertThat(persisted.course().getId()).isNotNull();
        assertThat(persisted.course().getStatus()).isEqualTo(CourseStatus.READY);
        assertThat(persisted.course().getGenerationReason()).isEqualTo(GenerationReason.INITIAL);
        assertThat(persisted.course().getParentCourse()).isNull();
        assertThat(persisted.course().getUserId()).isNull();
        assertThat(persisted.course().getSavedAt()).isNull();

        CourseItem item = persisted.itemsByCandidateId().get("KTO-1001");
        assertThat(persisted.categoryNamesByCandidateId().get("KTO-1001"))
                .isEqualTo("관광지");
        assertThat(item.getId()).isNotNull();
        assertThat(item.getCourse().getId()).isEqualTo(persisted.course().getId());
        assertThat(item.getPlace().getId()).isNotNull();
        assertThat(item.getDayNo()).isEqualTo(1);
        assertThat(item.getPosition()).isEqualTo(1);
        assertThat(item.getRecommendationScore()).isNull();
        assertThat(item.getItemSource()).isEqualTo(CourseItemSource.USER_FIXED);
        assertThat(mappingRepository.findBySourceCodeAndSourcePlaceId("KTO", "KTO-1001"))
                .get()
                .extracting(mapping -> mapping.getPlace().getId())
                .isEqualTo(item.getPlace().getId());

        CourseResponseDto response = new CourseResponseAssembler().assemble(
                input, result, List.of(candidate), persisted);
        assertThat(response.id()).isEqualTo(persisted.course().getId());
        assertThat(response.days().get(0).items().get(0).id()).isEqualTo(item.getId());
        assertThat(response.days().get(0).items().get(0).courseId())
                .isEqualTo(persisted.course().getId());
        assertThat(response.days().get(0).items().get(0).placeId())
                .isEqualTo(item.getPlace().getId());
        assertThat(response.days().get(0).items().get(0).categoryName())
                .isEqualTo("관광지");
    }

    @Test
    void reusesExistingKtoMappingWithoutDuplicatingPlace() throws Exception {
        CourseCandidateDto candidate = candidate(
                "KTO-2001", "비자림", "제주특별자치도 제주시 구좌읍", "A01");
        CourseAiInputDto input = input(List.of(fact(candidate, null)), false);
        CourseAiResultDto result = result("KTO-2001", "2026-08-27", "10:00");

        CoursePersistenceResult first = persistenceService.persist(
                request(), input, result, List.of(candidate));
        CoursePersistenceResult second = persistenceService.persist(
                request(),
                input(List.of(fact(candidate, null)), false, GenerationReason.USER_REGENERATE),
                result,
                List.of(candidate));

        assertThat(courseRepository.count()).isEqualTo(2);
        assertThat(courseItemRepository.count()).isEqualTo(2);
        assertThat(placeRepository.count()).isEqualTo(1);
        assertThat(mappingRepository.count()).isEqualTo(1);
        assertThat(second.course().getId()).isNotEqualTo(first.course().getId());
        assertThat(second.course().getGenerationReason())
                .isEqualTo(GenerationReason.USER_REGENERATE);
        assertThat(first.itemsByCandidateId().get("KTO-2001").getPlace().getId())
                .isEqualTo(second.itemsByCandidateId().get("KTO-2001").getPlace().getId());
    }

    @Test
    void persistsNullableSelfReferenceForRegeneratedCourse() {
        Course parent = courseRepository.save(Course.ready(
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 29),
                2,
                500000,
                Transport.RENTAL_CAR,
                GenerationReason.INITIAL,
                "course-ai-1"));
        Course child = courseRepository.save(Course.ready(
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 29),
                2,
                500000,
                Transport.RENTAL_CAR,
                GenerationReason.USER_REGENERATE,
                "course-ai-1",
                parent));

        courseRepository.flush();

        assertThat(child.getParentCourse()).isSameAs(parent);
        assertThat(child.getParentCourse().getId()).isEqualTo(parent.getId());
    }

    @Test
    void preservesAiDayAndPositionOrder() throws Exception {
        CourseCandidateDto first = candidate(
                "KTO-A", "첫 장소", "제주특별자치도 제주시 구좌읍", "A01");
        CourseCandidateDto second = candidate(
                "KTO-B", "둘째 장소", "제주특별자치도 제주시 구좌읍", "A01");
        CourseAiInputDto input = input(
                List.of(fact(first, null), fact(second, null)), false);
        CourseAiResultDto result = new CourseAiResultDto("1.0", List.of(
                new CourseAiResultDto.DayDto(
                        LocalDate.of(2026, 8, 27),
                        List.of(
                                item("KTO-B", "09:00"),
                                item("KTO-A", "11:00")))));

        CoursePersistenceResult persisted = persistenceService.persist(
                request(), input, result, List.of(first, second));
        List<CourseItem> items = courseItemRepository.findAllByCourseIdOrderByDayNoAscPositionAsc(
                persisted.course().getId());

        assertThat(items).extracting(CourseItem::getId)
                .containsExactly(
                        persisted.itemsByCandidateId().get("KTO-B").getId(),
                        persisted.itemsByCandidateId().get("KTO-A").getId());
        assertThat(items).extracting(CourseItem::getDayNo).containsExactly(1, 1);
        assertThat(items).extracting(CourseItem::getPosition).containsExactly(1, 2);
    }

    @Test
    void rollsBackCoursePlaceMappingAndItemsWhenRequiredReferenceIsUnavailable() throws Exception {
        CourseCandidateDto valid = candidate(
                "KTO-OK", "정상 장소", "제주특별자치도 제주시 구좌읍", "A01");
        CourseCandidateDto unsupported = candidate(
                "KTO-BAD", "미분류 장소", "제주특별자치도 제주시 구좌읍", "A04");
        CourseAiInputDto input = input(
                List.of(fact(valid, null), fact(unsupported, null)), false);
        CourseAiResultDto result = new CourseAiResultDto("1.0", List.of(
                new CourseAiResultDto.DayDto(
                        LocalDate.of(2026, 8, 27),
                        List.of(item("KTO-OK", "09:00"), item("KTO-BAD", "11:00")))));

        assertThatThrownBy(() -> persistenceService.persist(
                request(), input, result, List.of(valid, unsupported)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");

        assertThat(courseRepository.count()).isZero();
        assertThat(courseItemRepository.count()).isZero();
        assertThat(placeRepository.count()).isZero();
        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void rollsBackEverythingWhenRegionReferenceIsUnavailable() throws Exception {
        CourseCandidateDto candidate = candidate(
                "KTO-NORTH", "북부 장소", "제주특별자치도 제주시", "A01");
        CourseAiInputDto.CandidateFactDto northFact = new CourseAiInputDto.CandidateFactDto(
                new CourseAiInputDto.PlaceIdentityDto(
                        "KTO-NORTH", null, "KTO", "KTO-NORTH"),
                "북부 장소",
                "제주특별자치도 제주시",
                33.50,
                126.53,
                new CourseAiInputDto.TourCategoryDto("A01", null, null),
                "NORTH",
                null,
                List.of(),
                List.of(),
                null);
        CourseAiInputDto input = input(List.of(northFact), false);

        assertThatThrownBy(() -> persistenceService.persist(
                request(),
                input,
                result("KTO-NORTH", "2026-08-27", "09:00"),
                List.of(candidate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("권역");

        assertThat(courseRepository.count()).isZero();
        assertThat(courseItemRepository.count()).isZero();
        assertThat(placeRepository.count()).isZero();
        assertThat(mappingRepository.count()).isZero();
    }

    @Test
    void doesNotCreateReadyCourseForEmptyAiSchedule() throws Exception {
        CourseCandidateDto candidate = candidate(
                "KTO-EMPTY", "빈 일정 후보", "제주특별자치도 제주시 구좌읍", "A01");
        CourseAiInputDto input = input(List.of(fact(candidate, null)), false);

        assertThatThrownBy(() -> persistenceService.persist(
                request(),
                input,
                new CourseAiResultDto("1.0", List.of()),
                List.of(candidate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("방문 일정");

        assertThat(courseRepository.count()).isZero();
        assertThat(courseItemRepository.count()).isZero();
    }

    @Test
    void persistsUnmatchedKakaoWantWithActualSourceIdentityAndFixedSchedule()
            throws Exception {
        CourseRequestDto request = kakaoRequest(
                "카카오 숲길",
                "KAKAO-9001",
                "제주특별자치도 제주시 구좌읍 비자숲길 1");
        CourseAiInputDto input = new CourseAiInputAssembler().assemble(
                request,
                List.of(),
                java.util.Map.of(),
                List.of(),
                new CourseAiInputDto.GenerationMetadataDto(
                        GenerationReason.INITIAL, "course-ai-1", null));
        String candidateId = input.candidates().get(0).identity().candidateId();
        CourseAiResultDto result = result(
                input.contractVersion(), candidateId, "2026-08-27", "10:30");

        new com.example.hangat.course.ai.CourseAiResultValidator()
                .validate(input, result);
        CoursePersistenceResult persisted = persistenceService.persist(
                request, input, result, List.of());

        CourseItem item = persisted.itemsByCandidateId().get(candidateId);
        assertThat(persisted.course().getId()).isNotNull();
        assertThat(item.getId()).isNotNull();
        assertThat(item.getPlace().getId()).isNotNull();
        assertThat(item.getItemSource()).isEqualTo(CourseItemSource.USER_FIXED);
        assertThat(item.getVisitDate()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(item.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(persisted.categoryNamesByCandidateId().get(candidateId))
                .isEqualTo("관광지");
        assertThat(mappingRepository.findBySourceCodeAndSourcePlaceId(
                "KAKAO_LOCAL", "KAKAO-9001"))
                .get()
                .extracting(mapping -> mapping.getPlace().getId())
                .isEqualTo(item.getPlace().getId());
        assertThat(mappingRepository.findBySourceCodeAndSourcePlaceId(
                "KTO", "KAKAO-9001")).isEmpty();

        CourseResponseDto response = new CourseResponseAssembler().assemble(
                input, result, List.of(), persisted);
        CourseResponseDto.ItemDto responseItem = response.days().get(0).items().get(0);
        assertThat(responseItem.placeId()).isEqualTo(item.getPlace().getId());
        assertThat(responseItem.placeName()).isEqualTo("카카오 숲길");
        assertThat(responseItem.categoryName()).isEqualTo("관광지");
        assertThat(responseItem.imageUrl()).isNull();
        assertThat(responseItem.tourCategory()).isNull();
        assertThat(responseItem.congestion()).isEmpty();
        assertThat(responseItem.weather()).isNull();
    }

    @Test
    void reusesCanonicalPlaceWhenKakaoWantMatchesKtoCandidate() throws Exception {
        CourseRequestDto request = kakaoRequest(
                "비자림",
                "KAKAO-125266",
                "제주특별자치도 제주시 구좌읍 비자숲길 55");
        CourseCandidateDto ktoCandidate = candidate(
                "125266", "비자림", "제주특별자치도 제주시 구좌읍 비자숲길 55", "A01");
        CourseAiInputDto input = new CourseAiInputAssembler().assemble(
                request,
                List.of(ktoCandidate),
                java.util.Map.of(),
                List.of(),
                new CourseAiInputDto.GenerationMetadataDto(
                        GenerationReason.INITIAL, "course-ai-1", null));
        String candidateId = input.candidates().get(0).identity().candidateId();

        CoursePersistenceResult persisted = persistenceService.persist(
                request,
                input,
                result(input.contractVersion(), candidateId, "2026-08-27", "10:30"),
                List.of(ktoCandidate));

        Long placeId = persisted.itemsByCandidateId().get(candidateId).getPlace().getId();
        assertThat(placeRepository.count()).isEqualTo(1);
        assertThat(mappingRepository.count()).isEqualTo(2);
        assertThat(mappingRepository.findBySourceCodeAndSourcePlaceId("KTO", "125266"))
                .get().extracting(mapping -> mapping.getPlace().getId()).isEqualTo(placeId);
        assertThat(mappingRepository.findBySourceCodeAndSourcePlaceId(
                "KAKAO_LOCAL", "KAKAO-125266"))
                .get().extracting(mapping -> mapping.getPlace().getId()).isEqualTo(placeId);
    }

    private CourseRequestDto request() throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date":"2026-08-27",
                  "end_date":"2026-08-29",
                  "people":2,
                  "budget_total":500000,
                  "transport":"RENTAL_CAR",
                  "course_regions":[],
                  "course_styles":[{"code":"NATURE","weight":1}],
                  "course_place_preferences":[]
                }
                """, CourseRequestDto.class);
    }

    private CourseRequestDto kakaoRequest(
            String placeName,
            String sourcePlaceId,
            String address
    ) throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date":"2026-08-27",
                  "end_date":"2026-08-29",
                  "people":2,
                  "budget_total":500000,
                  "transport":"RENTAL_CAR",
                  "course_regions":[{"region_id":1,"code":"EAST","name":"동부"}],
                  "course_styles":[{"code":"NATURE","weight":1}],
                  "course_place_preferences":[{
                    "source_code":"KAKAO_LOCAL",
                    "source_place_id":"%s",
                    "place_name":"%s",
                    "road_address":"%s",
                    "latitude":33.458,
                    "longitude":126.942,
                    "category_name":"여행 > 관광,명소 > 자연명소",
                    "preference_type":"WANT",
                    "fixed_date":"2026-08-27",
                    "fixed_time":"10:30"
                  }]
                }
                """.formatted(sourcePlaceId, placeName, address), CourseRequestDto.class);
    }

    private CourseCandidateDto candidate(
            String contentId,
            String name,
            String address,
            String category1
    ) throws Exception {
        TourPlaceDto place = objectMapper.readValue("""
                {"contentid":"%s","title":"%s","addr1":"%s",
                 "mapy":33.458,"mapx":126.942,"cat1":"%s"}
                """.formatted(contentId, name, address, category1), TourPlaceDto.class);
        return new CourseCandidateDto(place, List.of(), null, List.of());
    }

    private CourseAiInputDto.CandidateFactDto fact(
            CourseCandidateDto candidate,
            PreferenceType preferenceType
    ) {
        TourPlaceDto place = candidate.getPlace();
        return new CourseAiInputDto.CandidateFactDto(
                new CourseAiInputDto.PlaceIdentityDto(
                        place.getContentId(), null, "KTO", place.getContentId()),
                place.getTitle(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                new CourseAiInputDto.TourCategoryDto(
                        place.getCategory(), place.getCategory2(), place.getCategory3()),
                "EAST",
                preferenceType,
                List.of(),
                List.of(),
                null);
    }

    private CourseAiInputDto input(
            List<CourseAiInputDto.CandidateFactDto> facts,
            boolean fixedWant
    ) {
        return input(facts, fixedWant, GenerationReason.INITIAL);
    }

    private CourseAiInputDto input(
            List<CourseAiInputDto.CandidateFactDto> facts,
            boolean fixedWant,
            GenerationReason generationReason
    ) {
        CourseAiInputDto.PlaceConstraintDto fixed = fixedWant
                ? new CourseAiInputDto.PlaceConstraintDto(
                        new CourseAiInputDto.PlaceIdentityDto(
                                "KTO-1001", null, "KTO", "KTO-1001"),
                        "성산일출봉", null, null, null, null,
                        PreferenceType.WANT,
                        LocalDate.of(2026, 8, 27),
                        LocalTime.of(9, 0))
                : null;
        return new CourseAiInputDto(
                "1.0",
                new CourseAiInputDto.TripConditionDto(
                        LocalDate.of(2026, 8, 27),
                        LocalDate.of(2026, 8, 29),
                        2,
                        500000,
                        Transport.RENTAL_CAR),
                new CourseAiInputDto.UserPreferencesDto(
                        List.of(),
                        List.of(),
                        fixed == null ? List.of() : List.of(fixed),
                        List.of(),
                        null),
                facts,
                List.of(),
                new CourseAiInputDto.GenerationMetadataDto(
                        generationReason, "course-ai-1", null));
    }

    private CourseAiResultDto result(
            String candidateId,
            String date,
            String startTime
    ) {
        return result("1.0", candidateId, date, startTime);
    }

    private CourseAiResultDto result(
            String contractVersion,
            String candidateId,
            String date,
            String startTime
    ) {
        return new CourseAiResultDto(contractVersion, List.of(
                new CourseAiResultDto.DayDto(
                        LocalDate.parse(date),
                        List.of(item(candidateId, startTime)))));
    }

    private CourseAiResultDto.ItemDto item(String candidateId, String startTime) {
        return new CourseAiResultDto.ItemDto(
                candidateId,
                LocalTime.parse(startTime),
                "사실 데이터에 근거한 추천 이유");
    }
}
