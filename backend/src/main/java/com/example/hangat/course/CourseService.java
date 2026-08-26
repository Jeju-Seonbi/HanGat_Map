package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiGenerationService;
import com.example.hangat.course.model.AccommodationDto;
import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRegionDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.CourseStyleDto;
import com.example.hangat.course.model.PlacePreferenceDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.example.hangat.course.model.Transport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final TourApiService tourApiService;
    private final CongestionApiService congestionApiService;
    private final CourseAiPreparationService courseAiPreparationService;
    private final CourseAiGenerationService courseAiGenerationService;

    public void createCourse(CourseRequestDto request) {
        CourseAiInputDto input = prepareAiInput(request);
        courseAiGenerationService.generate(input);
    }

    CourseAiInputDto prepareAiInput(CourseRequestDto request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Integer people = request.getPeople();
        Integer budgetTotal = request.getBudgetTotal();
        Transport transport = request.getTransport();
        List<CourseRegionDto> courseRegions = request.getCourseRegions();
        List<CourseStyleDto> courseStyles = request.getCourseStyles();
        List<PlacePreferenceDto> coursePlacePreferences = request.getCoursePlacePreferences();
        AccommodationDto accommodation = request.getAccommodation();

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("여행 날짜는 필수입니다.");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }

        if (people == null || people <= 0) {
            throw new IllegalArgumentException("인원은 1명 이상이어야 합니다.");
        }

        if (budgetTotal == null || budgetTotal <= 0) {
            throw new IllegalArgumentException("예산은 0원보다 커야 합니다.");
        }

        if (transport == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }

        if (courseStyles == null || courseStyles.isEmpty()) {
            throw new IllegalArgumentException("여행 스타일은 1개 이상 선택해야 합니다.");
        }

        if (courseRegions == null) {
            throw new IllegalArgumentException("권역 정보가 필요합니다.");
        }

        validatePlacePreferences(coursePlacePreferences, startDate, endDate);

        List<TourPlaceDto> tourPlaces = tourApiService.getTourPlaces();

        System.out.println("관광지 개수 = " + tourPlaces.size());

        for (TourPlaceDto place : tourPlaces) {
            System.out.println(
                    place.getTitle()
                            + " / "
                            + place.getAddress()
                            + " / "
                            + place.getLatitude()
                            + " / "
                            + place.getLongitude()
            );
        }

        if (tourPlaces.isEmpty()) {
            throw new IllegalArgumentException("조회된 관광지가 없습니다.");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        List<CourseCandidateDto> courseCandidates = new ArrayList<>();

        for (TourPlaceDto place : tourPlaces) {
            PreferenceType preferenceType = findPreferenceType(
                    place,
                    coursePlacePreferences
            );

            if (!CourseCandidateRegionFilter.shouldInclude(
                    place.getAddress(),
                    preferenceType,
                    courseRegions
            )) {
                continue;
            }

            List<String> confirmedStyleHints =
                    TourPlaceStyleHintResolver.resolve(place);

            String signguCd;

            if (place.getAddress() != null && place.getAddress().contains("제주시")) {
                signguCd = "50110";
            } else if (place.getAddress() != null && place.getAddress().contains("서귀포시")) {
                signguCd = "50130";
            } else {
                courseCandidates.add(new CourseCandidateDto(
                        place,
                        Collections.emptyList(),
                        preferenceType,
                        confirmedStyleHints
                ));
                continue;
            }

            List<CongestionDto> congestionData = congestionApiService.getCongestionData(
                    signguCd,
                    place.getTitle()
            );

            System.out.println("전체 혼잡도 데이터 개수 = " + congestionData.size());

            if (congestionData.isEmpty()) {
                courseCandidates.add(new CourseCandidateDto(
                        place,
                        Collections.emptyList(),
                        preferenceType,
                        confirmedStyleHints
                ));
                continue;
            }

            List<CongestionDto> filteredCongestionData = congestionData.stream()
                    .filter(congestion -> {
                        LocalDate congestionDate = LocalDate.parse(
                                congestion.getBaseYmd(),
                                formatter
                        );

                        return !congestionDate.isBefore(startDate)
                                && !congestionDate.isAfter(endDate);
                    })
                    .toList();

            courseCandidates.add(new CourseCandidateDto(
                    place,
                    filteredCongestionData,
                    preferenceType,
                    confirmedStyleHints
            ));
        }

        System.out.println("코스 추천 후보 개수 = " + courseCandidates.size());
        return courseAiPreparationService.prepare(request, courseCandidates);
    }

    private void validatePlacePreferences(
            List<PlacePreferenceDto> preferences,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (preferences == null || preferences.isEmpty()) {
            return;
        }

        for (int i = 0; i < preferences.size(); i++) {
            PlacePreferenceDto first = preferences.get(i);

            if (first == null || first.getPreferenceType() == null) {
                continue;
            }

            validateFixedSchedule(first, startDate, endDate);

            for (int j = i + 1; j < preferences.size(); j++) {
                PlacePreferenceDto second = preferences.get(j);

                if (second == null || second.getPreferenceType() == null) {
                    continue;
                }

                if (isSamePreferredPlace(first, second)) {
                    if (first.getPreferenceType() == second.getPreferenceType()) {
                        throw new IllegalArgumentException(
                                "동일한 장소를 중복 지정할 수 없습니다."
                        );
                    }
                    throw new IllegalArgumentException(
                            "동일한 장소를 WANT와 AVOID에 동시에 지정할 수 없습니다."
                    );
                }
            }
        }
    }

    private void validateFixedSchedule(
            PlacePreferenceDto preference,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate fixedDate = preference.getFixedDate();

        if (preference.getPreferenceType() == PreferenceType.AVOID
                && (fixedDate != null || preference.getFixedTime() != null)) {
            throw new IllegalArgumentException(
                    "AVOID 장소에는 고정 날짜나 시간을 지정할 수 없습니다."
            );
        }

        if (preference.getFixedTime() != null && fixedDate == null) {
            throw new IllegalArgumentException(
                    "고정 시간은 고정 날짜와 함께 지정해야 합니다."
            );
        }

        if (fixedDate != null
                && (fixedDate.isBefore(startDate) || fixedDate.isAfter(endDate))) {
            throw new IllegalArgumentException("고정 방문일은 여행기간 내여야 합니다.");
        }
    }

    private boolean isSamePreferredPlace(
            PlacePreferenceDto first,
            PlacePreferenceDto second
    ) {
        if (first.getPlaceId() != null && second.getPlaceId() != null) {
            return first.getPlaceId().equals(second.getPlaceId());
        }

        if (hasSourceIdentity(first) && hasSourceIdentity(second)) {
            return first.getSourceCode().trim().equalsIgnoreCase(second.getSourceCode().trim())
                    && first.getSourcePlaceId().trim().equals(second.getSourcePlaceId().trim());
        }

        String firstName = normalizePlaceName(first.getPlaceName());
        String secondName = normalizePlaceName(second.getPlaceName());

        return !firstName.isEmpty() && firstName.equals(secondName);
    }

    private boolean hasSourceIdentity(PlacePreferenceDto preference) {
        return preference.getSourceCode() != null
                && !preference.getSourceCode().isBlank()
                && preference.getSourcePlaceId() != null
                && !preference.getSourcePlaceId().isBlank();
    }

    private PreferenceType findPreferenceType(
            TourPlaceDto place,
            List<PlacePreferenceDto> preferences
    ) {
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }

        PreferenceType matchedPreferenceType = null;

        for (PlacePreferenceDto preference : preferences) {
            if (preference == null
                    || preference.getPreferenceType() == null
                    || !matchesTourPlace(place, preference)) {
                continue;
            }

            if (preference.getPreferenceType() == PreferenceType.AVOID) {
                return PreferenceType.AVOID;
            }

            matchedPreferenceType = PreferenceType.WANT;
        }

        return matchedPreferenceType;
    }

    private boolean matchesTourPlace(
            TourPlaceDto place,
            PlacePreferenceDto preference
    ) {
        String placeTitle = normalizePlaceName(place.getTitle());
        String preferenceName = normalizePlaceName(preference.getPlaceName());

        return !placeTitle.isEmpty() && placeTitle.equals(preferenceName);
    }

    private String normalizePlaceName(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
