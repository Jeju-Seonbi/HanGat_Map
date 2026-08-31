package com.example.hangat.course;

import com.example.hangat.course.model.RegionCode;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class TourPlaceRegionResolver {

    private static final Map<String, RegionCode> DETAILED_REGION_CODES = Map.ofEntries(
            Map.entry("조천읍", RegionCode.NORTH),
            Map.entry("구좌읍", RegionCode.EAST),
            Map.entry("성산읍", RegionCode.EAST),
            Map.entry("표선면", RegionCode.EAST),
            Map.entry("남원읍", RegionCode.SOUTH),
            Map.entry("애월읍", RegionCode.WEST),
            Map.entry("한림읍", RegionCode.WEST),
            Map.entry("한경면", RegionCode.WEST),
            Map.entry("대정읍", RegionCode.WEST),
            Map.entry("안덕면", RegionCode.WEST)
    );

    private static final Pattern UNMAPPED_TOWN_PATTERN = Pattern.compile("[가-힣0-9]+(?:읍|면)");

    private TourPlaceRegionResolver() {
    }

    static Optional<RegionCode> resolve(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        for (Map.Entry<String, RegionCode> entry : DETAILED_REGION_CODES.entrySet()) {
            if (address.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        if (UNMAPPED_TOWN_PATTERN.matcher(address).find()) {
            return Optional.empty();
        }

        if (address.contains("제주시")) {
            return Optional.of(RegionCode.NORTH);
        }

        if (address.contains("서귀포시")) {
            return Optional.of(RegionCode.SOUTH);
        }

        return Optional.empty();
    }
}
