package com.example.hangat.domain.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 중기 기온 아이템
@JsonIgnoreProperties(ignoreUnknown = true)
public record MidTaItem(
        Integer taMin3, Integer taMax3,
        Integer taMin4, Integer taMax4,
        Integer taMin5, Integer taMax5,
        Integer taMin6, Integer taMax6,
        Integer taMin7, Integer taMax7
) {
}
