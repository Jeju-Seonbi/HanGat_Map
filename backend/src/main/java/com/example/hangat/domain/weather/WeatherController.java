package com.example.hangat.domain.weather;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.domain.weather.model.DailyWeather;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/main")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/weather")
    @Operation(summary = "주간 날씨 조회", description = "제주 7일 예보 - 기상청 단기(D+0~3)와 중기(D+4~6)를 병합해 날짜순으로 반환한다.")
    public BaseResponse<List<DailyWeather>> weeklyWeather() {
        return BaseResponse.success(weatherService.getWeeklyForecast());
    }
}
