package com.example.hangat.course;

import com.example.hangat.course.facts.CourseCandidate;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.TourPlaceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCandidateNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final CourseCandidateNormalizer normalizer = new CourseCandidateNormalizer();

    @Test
    void normalizesKtoCandidateToProviderNeutralCandidate() throws Exception {
        CourseCandidate result = normalizeKto("kto-1", "A01", "A0101", "A01010100",
                List.of("NATURE"));

        assertThat(result.place().name()).isEqualTo("비자림");
        assertThat(result.place().address()).contains("구좌읍");
        assertThat(result.place().latitude()).isEqualByComparingTo("33.485");
        assertThat(result.place().longitude()).isEqualByComparingTo("126.811");
        assertThat(result.regionCode()).isEqualTo("EAST");
    }

    @Test
    void preservesKtoSourceIdentity() throws Exception {
        CourseCandidate result = normalizeKto("125266", "A01", null, null, List.of());

        assertThat(result.identity().candidateId()).isEqualTo("125266");
        assertThat(result.identity().sourceCode()).isEqualTo("KTO");
        assertThat(result.identity().sourcePlaceId()).isEqualTo("125266");
    }

    @Test
    void preservesKtoClassificationLevels() throws Exception {
        CourseCandidate result = normalizeKto(
                "kto-2", "A01", "A0101", "A01010100", List.of());

        assertThat(result.externalClassifications()).singleElement().satisfies(fact -> {
            assertThat(fact.sourceCode()).isEqualTo("KTO");
            assertThat(fact.level1Code()).isEqualTo("A01");
            assertThat(fact.level2Code()).isEqualTo("A0101");
            assertThat(fact.level3Code()).isEqualTo("A01010100");
        });
    }

    @Test
    void mapsNatureStyleFromConfirmedA01Evidence() throws Exception {
        CourseCandidate result = normalizeKto("nature", "A01", null, null,
                List.of("NATURE"));

        assertThat(result.styleHints()).singleElement().satisfies(hint -> {
            assertThat(hint.styleCode()).isEqualTo("NATURE");
            assertThat(hint.evidenceSource()).isEqualTo("KTO_CAT1");
            assertThat(hint.evidenceValue()).isEqualTo("A01");
        });
    }

    @Test
    void mapsActivityStyleWithoutCreatingActivityPlaceCategory() throws Exception {
        CourseCandidate result = normalizeKto("activity", "A03", null, null,
                List.of("ACTIVITY"));

        assertThat(result.styleHints()).extracting("styleCode").containsExactly("ACTIVITY");
        assertThat(result.internalPlaceCategory().code()).isEqualTo("TOURIST");
    }

    @Test
    void mapsCafeStyleFromConfirmedCat3Evidence() throws Exception {
        CourseCandidate result = normalizeKto(
                "cafe", "A05", "A0502", "A05020900", List.of("CAFE"));

        assertThat(result.styleHints()).extracting("styleCode").containsExactly("CAFE");
        assertThat(result.internalPlaceCategory().code()).isEqualTo("CAFE");
    }

    @Test
    void normalizesKakaoWantCandidate() throws Exception {
        CourseCandidate result = normalizeKakaoWant(false).candidates().get(0);

        assertThat(result.identity().sourceCode()).isEqualTo("KAKAO_LOCAL");
        assertThat(result.identity().sourcePlaceId()).isEqualTo("kakao-777");
        assertThat(result.place().name()).isEqualTo("카카오 숲길");
        assertThat(result.userConstraint().preferenceType()).isEqualTo(PreferenceType.WANT);
    }

    @Test
    void preservesKakaoFixedSchedule() throws Exception {
        CourseCandidate result = normalizeKakaoWant(false).candidates().get(0);

        assertThat(result.userConstraint().fixedDate())
                .isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(result.userConstraint().fixedTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void doesNotInventKtoClassificationForKakaoCandidate() throws Exception {
        CourseCandidate result = normalizeKakaoWant(false).candidates().get(0);

        assertThat(result.externalClassifications()).isEmpty();
        assertThat(result.styleHints()).isEmpty();
        assertThat(result.internalPlaceCategory()).isNull();
    }

    @Test
    void preventsKtoAndKakaoCandidateIdCollision() throws Exception {
        CourseCandidateDto kto = candidate(
                tourPlace("request-want-1", "기존 후보", "A01", null, null, true),
                null, List.of("NATURE"));
        CourseCandidateNormalizer.NormalizationResult result = normalizer.normalize(
                kakaoWantRequest(false), List.of(kto));

        assertThat(result.candidates()).extracting(candidate -> candidate.identity().candidateId())
                .containsExactly("request-want-1", "request-want-2")
                .doesNotHaveDuplicates();
    }

    @Test
    void excludesAvoidCandidate() throws Exception {
        CourseCandidateDto avoid = candidate(
                tourPlace("avoid-1", "제외 장소", "A01", null, null, true),
                PreferenceType.AVOID, List.of("NATURE"));

        assertThat(normalizer.normalize(emptyRequest(), List.of(avoid)).candidates()).isEmpty();
    }

    @Test
    void keepsMissingCoordinatesAndClassificationsAbsent() throws Exception {
        TourPlaceDto place = objectMapper.readValue("""
                {
                  "contentid": "missing-facts",
                  "title": "정보가 적은 장소",
                  "addr1": "주소 미상"
                }
                """, TourPlaceDto.class);

        CourseCandidate result = normalizer.normalize(
                emptyRequest(), List.of(candidate(place, null, List.of())))
                .candidates().get(0);

        assertThat(result.place().latitude()).isNull();
        assertThat(result.place().longitude()).isNull();
        assertThat(result.externalClassifications()).isEmpty();
        assertThat(result.internalPlaceCategory()).isNull();
        assertThat(result.styleHints()).isEmpty();
    }

    private CourseCandidate normalizeKto(
            String contentId,
            String cat1,
            String cat2,
            String cat3,
            List<String> hints
    ) throws Exception {
        CourseCandidateDto candidate = candidate(
                tourPlace(contentId, "비자림", cat1, cat2, cat3, true), null, hints);
        return normalizer.normalize(emptyRequest(), List.of(candidate)).candidates().get(0);
    }

    private CourseCandidateNormalizer.NormalizationResult normalizeKakaoWant(
            boolean includeCategory
    ) throws Exception {
        return normalizer.normalize(kakaoWantRequest(includeCategory), List.of());
    }

    private CourseRequestDto emptyRequest() throws Exception {
        return objectMapper.readValue("""
                {
                  "course_place_preferences": []
                }
                """, CourseRequestDto.class);
    }

    private CourseRequestDto kakaoWantRequest(boolean includeCategory) throws Exception {
        String category = includeCategory
                ? ", \"category_name\": \"여행 > 관광,명소\""
                : "";
        return objectMapper.readValue("""
                {
                  "course_place_preferences": [
                    {
                      "source_code": "KAKAO_LOCAL",
                      "source_place_id": "kakao-777",
                      "place_name": "카카오 숲길",
                      "road_address": "제주특별자치도 제주시 구좌읍 숲길 1",
                      "latitude": 33.485,
                      "longitude": 126.811,
                      "preference_type": "WANT",
                      "fixed_date": "2026-08-28",
                      "fixed_time": "10:30"%s
                    }
                  ]
                }
                """.formatted(category), CourseRequestDto.class);
    }

    private CourseCandidateDto candidate(
            TourPlaceDto place,
            PreferenceType preferenceType,
            List<String> hints
    ) {
        return new CourseCandidateDto(place, List.of(), preferenceType, hints);
    }

    private TourPlaceDto tourPlace(
            String contentId,
            String title,
            String cat1,
            String cat2,
            String cat3,
            boolean coordinates
    ) throws Exception {
        String json = """
                {
                  "contentid": "%s",
                  "title": "%s",
                  "addr1": "제주특별자치도 제주시 구좌읍 비자숲길 55",
                  "mapy": %s,
                  "mapx": %s,
                  "cat1": %s,
                  "cat2": %s,
                  "cat3": %s,
                  "firstimage": "https://example.invalid/place.jpg"
                }
                """.formatted(
                contentId,
                title,
                coordinates ? "33.485" : "null",
                coordinates ? "126.811" : "null",
                jsonString(cat1),
                jsonString(cat2),
                jsonString(cat3));
        return objectMapper.readValue(json, TourPlaceDto.class);
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
