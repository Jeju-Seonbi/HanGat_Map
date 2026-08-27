package com.example.hangat.course;

import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCandidateShortlistServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final CourseCandidateShortlistService service =
            new CourseCandidateShortlistService();

    @Test
    void limitsOrdinaryCandidatesWhileKeepingWantAndExcludingAvoidAndOtherRegions()
            throws Exception {
        List<TourPlaceDto> raw = new ArrayList<>();
        raw.add(place("want", "서부 WANT", "제주특별자치도 제주시 애월읍", "A01"));
        raw.add(place("avoid", "제외 장소", "제주특별자치도 제주시 구좌읍", "A01"));
        raw.add(place("west", "서부 일반", "제주특별자치도 제주시 한림읍", "A01"));
        raw.add(place("unknown", "주소 불명", "주소 미상", "A01"));
        for (int index = 0; index < 40; index++) {
            raw.add(place("east-" + index, "동부 장소 " + index,
                    "제주특별자치도 제주시 구좌읍", index % 2 == 0 ? "A01" : "A02"));
        }

        List<CourseCandidateShortlistService.ShortlistedPlace> result = service.select(
                request("""
                        [{"code":"EAST","name":"동부"}]
                        """, """
                        [
                          {"place_name":"서부 WANT","preference_type":"WANT"},
                          {"place_name":"제외 장소","preference_type":"AVOID"}
                        ]
                        """),
                raw);

        assertThat(result).hasSize(15);
        assertThat(result).anySatisfy(candidate -> {
            assertThat(candidate.place().getContentId()).isEqualTo("want");
            assertThat(candidate.preferenceType()).isEqualTo(PreferenceType.WANT);
        });
        assertThat(result).extracting(candidate -> candidate.place().getContentId())
                .doesNotContain("avoid", "west", "unknown");
    }

    @Test
    void keepsCandidatesWithoutStyleHintsBecauseStyleIsSoft() throws Exception {
        TourPlaceDto nature = place(
                "nature", "자연 장소", "제주특별자치도 제주시 구좌읍", "A01");
        TourPlaceDto noHint = place(
                "no-hint", "문화 장소", "제주특별자치도 제주시 구좌읍", "A02");

        List<CourseCandidateShortlistService.ShortlistedPlace> result = service.select(
                request("[]", "[]"), List.of(nature, noHint));

        assertThat(result).extracting(candidate -> candidate.place().getContentId())
                .containsExactly("nature", "no-hint");
    }

    @Test
    void wantCandidatesRemainEvenWhenTheyExceedNormalShortlistLimit() throws Exception {
        List<TourPlaceDto> raw = new ArrayList<>();
        StringBuilder preferences = new StringBuilder("[");
        for (int index = 0; index < 16; index++) {
            if (index > 0) {
                preferences.append(',');
            }
            String name = "필수 장소 " + index;
            raw.add(place("want-" + index, name,
                    "제주특별자치도 제주시 애월읍", "A01"));
            preferences.append("{\"place_name\":\"")
                    .append(name)
                    .append("\",\"preference_type\":\"WANT\"}");
        }
        preferences.append(']');

        List<CourseCandidateShortlistService.ShortlistedPlace> result = service.select(
                request("[{\"code\":\"EAST\"}]", preferences.toString()), raw);

        assertThat(result).hasSize(16);
        assertThat(result).allMatch(candidate ->
                candidate.preferenceType() == PreferenceType.WANT);
    }

    @Test
    void removesDuplicateContentIdsBeforeShortlisting() throws Exception {
        TourPlaceDto first = place(
                "duplicate", "첫 장소", "제주특별자치도 제주시 구좌읍", "A01");
        TourPlaceDto duplicate = place(
                "duplicate", "중복 장소", "제주특별자치도 제주시 구좌읍", "A02");

        List<CourseCandidateShortlistService.ShortlistedPlace> result = service.select(
                request("[]", "[]"), List.of(first, duplicate));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).place().getTitle()).isEqualTo("첫 장소");
    }

    private CourseRequestDto request(String regions, String preferences) throws Exception {
        return objectMapper.readValue("""
                {
                  "start_date":"2026-08-28",
                  "end_date":"2026-08-29",
                  "people":2,
                  "budget_total":400000,
                  "transport":"RENTAL_CAR",
                  "course_regions":%s,
                  "course_styles":[{"code":"NATURE","weight":1}],
                  "course_place_preferences":%s
                }
                """.formatted(regions, preferences), CourseRequestDto.class);
    }

    private TourPlaceDto place(
            String contentId,
            String title,
            String address,
            String category
    ) throws Exception {
        return objectMapper.readValue("""
                {"contentid":"%s","title":"%s","addr1":"%s",
                 "mapy":33.4,"mapx":126.5,"cat1":"%s"}
                """.formatted(contentId, title, address, category), TourPlaceDto.class);
    }
}
