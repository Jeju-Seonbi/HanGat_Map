package com.example.hangat.course;

import com.example.hangat.course.model.CongestionDto;
import com.example.hangat.course.model.CongestionLevel;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.PreferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CongestionLevelResolverTest {

    @ParameterizedTest
    @CsvSource({
            "0, QUIET",
            "33.32, QUIET",
            "33.33, NORMAL",
            "33.34, NORMAL",
            "66.66, NORMAL",
            "66.67, CROWDED",
            "66.68, CROWDED",
            "100, CROWDED"
    })
    void resolvesThreeDisplayLevelsAtBoundaries(
            String rate,
            CongestionLevel expected
    ) {
        assertThat(CongestionLevelResolver.resolve(rate)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-number", "-0.01", "100.01"})
    void returnsEmptyWhenRateIsMissingOrInvalid(String rate) {
        assertThat(CongestionLevelResolver.resolve(rate)).isEmpty();
    }

    @Test
    void exposesOnlyTheOfficialThreeUserLabels() {
        assertThat(CongestionLevel.QUIET.getLabel()).isEqualTo("쾌적");
        assertThat(CongestionLevel.NORMAL.getLabel()).isEqualTo("보통");
        assertThat(CongestionLevel.CROWDED.getLabel()).isEqualTo("혼잡");
    }

    @Test
    void preservesRawRateAfterCalculatingDisplayLevel() throws Exception {
        CongestionDto congestion = congestionWithRate("66.67");

        assertThat(CongestionLevelResolver.resolve(congestion.getCnctrRate()))
                .contains(CongestionLevel.CROWDED);
        assertThat(congestion.getCnctrRate()).isEqualTo("66.67");
    }

    @Test
    void doesNotRemoveCrowdedWantCandidate() throws Exception {
        CongestionDto congestion = congestionWithRate("100");
        CourseCandidateDto candidate = new CourseCandidateDto(
                null,
                List.of(congestion),
                PreferenceType.WANT,
                List.of()
        );

        assertThat(CongestionLevelResolver.resolve(congestion.getCnctrRate()))
                .contains(CongestionLevel.CROWDED);
        assertThat(candidate.getPreferenceType()).isEqualTo(PreferenceType.WANT);
        assertThat(candidate.getCongestionData()).containsExactly(congestion);
    }

    private CongestionDto congestionWithRate(String rate) throws Exception {
        CongestionDto congestion = new CongestionDto();
        Field rateField = CongestionDto.class.getDeclaredField("cnctrRate");
        rateField.setAccessible(true);
        rateField.set(congestion, rate);
        return congestion;
    }
}
