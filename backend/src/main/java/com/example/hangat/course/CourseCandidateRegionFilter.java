package com.example.hangat.course;

import com.example.hangat.course.model.CourseRegionDto;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.RegionCode;

import java.util.List;

final class CourseCandidateRegionFilter {

    private CourseCandidateRegionFilter() {
    }

    static boolean shouldInclude(
            String address,
            PreferenceType preferenceType,
            List<CourseRegionDto> selectedRegions
    ) {
        if (preferenceType == PreferenceType.AVOID) {
            return false;
        }

        if (preferenceType == PreferenceType.WANT) {
            return true;
        }

        if (selectedRegions.isEmpty()) {
            return true;
        }

        return TourPlaceRegionResolver.resolve(address)
                .map(regionCode -> containsRegion(selectedRegions, regionCode))
                .orElse(false);
    }

    private static boolean containsRegion(
            List<CourseRegionDto> selectedRegions,
            RegionCode regionCode
    ) {
        return selectedRegions.stream()
                .filter(region -> region != null && region.getCode() != null)
                .anyMatch(region -> regionCode.name().equals(region.getCode()));
    }
}
