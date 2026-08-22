package com.example.hangat.domain.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 단기 예보 아이템
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShortTermItem(
        String category,
        String fcstDate,
        String fcstTime,
        String fcstValue
) {
}
