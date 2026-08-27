package com.example.hangat.domain.congestion;

import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.map.repository.CongestionForecastRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CongestionServiceTest {

    private final CongestionService service = new CongestionService(mock(CongestionForecastRepository.class));

    /** 임계값 경계 - 여유 <40 / 보통 <70 / 혼잡 <80 / 매우혼잡 >=80 (프론트와 동일해야 함) */
    @ParameterizedTest
    @CsvSource({
            "0,     RELAXED",
            "39.9,  RELAXED",
            "40,    MODERATE",
            "69.9,  MODERATE",
            "70,    CROWDED",
            "79.9,  CROWDED",
            "80,    VERY_CROWDED",
            "100,   VERY_CROWDED",
    })
    void 등급_경계값(double rate, CongestionLevel expected) {
        assertThat(service.levelOf(rate)).isEqualTo(expected);
    }

    @Test
    void 등급_한글_라벨() {
        assertThat(CongestionLevel.RELAXED.getLabel()).isEqualTo("여유");
        assertThat(CongestionLevel.VERY_CROWDED.getLabel()).isEqualTo("매우 혼잡");
    }
}
