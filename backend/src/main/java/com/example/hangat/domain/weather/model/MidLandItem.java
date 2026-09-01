package com.example.hangat.domain.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 중기 육상 예보 아이템
@JsonIgnoreProperties(ignoreUnknown = true)
public record MidLandItem(
        // wf3Am = 맑음/흐림/비 같은 텍스트
        String wf3Am,
        String wf4Am,
        String wf5Am,
        String wf6Am,
        String wf7Am,
        // rnSt3AM = 강수 확률(%)
        Integer rnSt3Am,
        Integer rnSt4Am,
        Integer rnSt5Am,
        Integer rnSt6Am,
        Integer rnSt7Am
) {
}
