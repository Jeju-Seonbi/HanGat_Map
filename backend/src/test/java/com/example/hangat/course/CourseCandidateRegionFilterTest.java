package com.example.hangat.course;

import com.example.hangat.course.model.CourseRegionDto;
import com.example.hangat.course.model.PreferenceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCandidateRegionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsEveryGeneralCandidateWhenAllRegionsAreSelected() {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "지역을 판단할 수 없는 주소",
                null,
                Collections.emptyList()
        )).isTrue();
    }

    @Test
    void keepsGeneralCandidateInSelectedRegion() throws Exception {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "제주특별자치도 제주시 구좌읍 비자숲길 55",
                null,
                List.of(region("EAST"))
        )).isTrue();
    }

    @Test
    void excludesGeneralCandidateOutsideSelectedRegion() throws Exception {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "제주특별자치도 제주시 애월읍 애월해안로 394",
                null,
                List.of(region("EAST"))
        )).isFalse();
    }

    @Test
    void keepsWantCandidateOutsideSelectedRegion() throws Exception {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "제주특별자치도 서귀포시 안덕면 신화역사로 15",
                PreferenceType.WANT,
                List.of(region("EAST"))
        )).isTrue();
    }

    @Test
    void excludesUnknownGeneralCandidateWhenRegionIsSelected() throws Exception {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "지역을 판단할 수 없는 주소",
                null,
                List.of(region("EAST"))
        )).isFalse();
    }

    @Test
    void keepsUnknownWantCandidateWhenRegionIsSelected() throws Exception {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "지역을 판단할 수 없는 주소",
                PreferenceType.WANT,
                List.of(region("EAST"))
        )).isTrue();
    }

    @Test
    void excludesAvoidCandidateRegardlessOfRegion() {
        assertThat(CourseCandidateRegionFilter.shouldInclude(
                "제주특별자치도 제주시 구좌읍 비자숲길 55",
                PreferenceType.AVOID,
                Collections.emptyList()
        )).isFalse();
    }

    private CourseRegionDto region(String code) throws Exception {
        return objectMapper.readValue(
                "{\"region_id\":1,\"code\":\"" + code + "\",\"name\":\"동부\"}",
                CourseRegionDto.class
        );
    }
}
