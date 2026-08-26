package com.example.hangat.course.weather;

import com.example.hangat.domain.weather.WeatherClient;
import com.example.hangat.domain.weather.model.ShortTermItem;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseWeatherService {

    private final WeatherClient weatherClient;

    public CourseWeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public List<CourseWeatherDto> getShortTermWeather(
            String baseDate,
            String baseTime,
            KmaGridPoint gridPoint,
            LocalDate forecastDate
    ) {
        if (gridPoint == null || forecastDate == null) {
            throw new IllegalArgumentException("기상청 격자와 예보 날짜는 필수입니다.");
        }

        List<ShortTermItem> items = weatherClient.fetchShortTerm(
                baseDate, baseTime, gridPoint.nx(), gridPoint.ny());
        return CourseWeatherMapper.map(items, forecastDate);
    }
}
