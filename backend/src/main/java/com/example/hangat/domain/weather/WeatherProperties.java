package com.example.hangat.domain.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public record WeatherProperties (
        String baseUrl,
        String serviceKey
) {

}
