package com.example.hangat.domain.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 기상청 공통 응답 wrapper
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaResponse<T>(Response<T> response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response<T>(Header header, Body<T> body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header<T>(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body<T>(Items<T> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items<T>(List<T> item) {
    }
}
