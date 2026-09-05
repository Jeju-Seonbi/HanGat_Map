package com.example.hangat.domain.weather;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.domain.weather.model.DailyWeather;
import com.example.hangat.domain.weather.model.MidLandItem;
import com.example.hangat.domain.weather.model.MidTaItem;
import com.example.hangat.domain.weather.model.ShortTermItem;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/*
* 주간(7일) 날씨 조립
* */

@Service
public class WeatherService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final WeatherClient client;

    public WeatherService(WeatherClient client) {
        this.client = client;
    }

    public List<DailyWeather> getWeeklyForecast() {
        // KST 고정 - 서버 시계가 UTC(OCI 기본)면 KST 새벽에 now()가 어제 날짜라 발표분·주간 창이 하루 밀린다
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));

        // 오늘 발표분 실패 시 어제 발표분으로 1회 출력
        List<ShortTermItem> shortTerm;

        try {
            shortTerm = client.fetchShortTerm(today.format(YMD), "0500");
        } catch (BaseException e) {
            shortTerm = client.fetchShortTerm(today.minusDays(1).format(YMD), "2300");
        }

        LocalDate midBaseDate = today;
        MidTaItem midTa;
        MidLandItem midLand;

        try {
            midTa = client.fetchMidTemperature(today.format(YMD) + "0600");
            midLand = client.fetchMidLand(today.format(YMD) + "0600");
        } catch (BaseException e) {
            midBaseDate = today.minusDays(1);
            midTa = client.fetchMidTemperature(midBaseDate.format(YMD) + "1800");
            midLand = client.fetchMidLand(midBaseDate.format(YMD) + "1800");
        }

        List<DailyWeather> week = new ArrayList<>();

        // 하루 요약 규칙은 WeatherDailySummarizer 한 곳에만 있다 - DB 적재(WeatherIngestService)와 같은 규칙
        for (int offset = 0; offset <= 3; offset++) {
            week.add(WeatherDailySummarizer.fromShortTerm(today.plusDays(offset), shortTerm).toDailyWeather());
        }

        for (int offset = 4; offset <= 6; offset++) {
            week.add(WeatherDailySummarizer.fromMid(today.plusDays(offset), midBaseDate, midTa, midLand)
                    .toDailyWeather());
        }

        return week;
    }
}
