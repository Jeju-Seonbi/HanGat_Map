package com.example.hangat.course;

import com.example.hangat.course.model.AccommodationDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.PlacePreferenceDto;
import com.example.hangat.course.model.Transport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseService {

    public void createCourse(CourseRequestDto request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Integer people = request.getPeople();
        Integer budgetTotal = request.getBudgetTotal();
        Transport transport = request.getTransport();
        List<String> regions = request.getRegions();
        List<String> styles = request.getStyles();
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

        if (styles == null || styles.isEmpty()) {
            throw new IllegalArgumentException("여행 스타일은 1개 이상 선택해야 합니다.");
        }

        if (regions == null) {
            throw new IllegalArgumentException("권역 정보가 필요합니다.");
        }
    }
}
