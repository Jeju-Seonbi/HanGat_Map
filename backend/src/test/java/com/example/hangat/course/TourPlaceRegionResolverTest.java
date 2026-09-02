package com.example.hangat.course;

import com.example.hangat.course.model.RegionCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TourPlaceRegionResolverTest {

    @ParameterizedTest
    @CsvSource({
            "제주특별자치도 제주시 구좌읍 비자숲길 55, EAST",
            "제주특별자치도 서귀포시 성산읍 일출로 284-12, EAST",
            "제주특별자치도 서귀포시 표선면 민속해안로 631-34, EAST",
            "제주특별자치도 제주시 애월읍 애월해안로 394, WEST",
            "제주특별자치도 제주시 한림읍 한림로 300, WEST",
            "제주특별자치도 제주시 한경면 고산리 3760, WEST",
            "제주특별자치도 서귀포시 대정읍 하모리 1, WEST",
            "제주특별자치도 서귀포시 안덕면 신화역사로 15, WEST",
            "제주특별자치도 서귀포시 남원읍 태위로 522-17, SOUTH",
            "제주특별자치도 제주시 조천읍 남조로 2023, NORTH",
            "제주특별자치도 제주시 관덕로14길 20, NORTH",
            "제주특별자치도 서귀포시 칠십리로 242, SOUTH"
    })
    void resolvesOfficialRegion(String address, RegionCode expected) {
        assertThat(TourPlaceRegionResolver.resolve(address)).contains(expected);
    }

    @Test
    void keepsUnmappedTownUnknownInsteadOfUsingCityFallback() {
        Optional<RegionCode> region = TourPlaceRegionResolver.resolve(
                "제주특별자치도 제주시 우도면 우도해안길 1"
        );

        assertThat(region).isEmpty();
    }
}
