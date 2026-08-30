package com.example.hangat.domain.weather.model;

import java.time.LocalDate;

public record DailyWeather(
        LocalDate date,
        Integer minTemp,
        Integer maxTemp,
        String sky,
        Integer rainProb
) {
}
