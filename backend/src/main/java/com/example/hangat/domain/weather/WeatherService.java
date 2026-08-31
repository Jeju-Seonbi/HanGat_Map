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
import java.util.Objects;

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

        for(int offset = 0; offset <= 3; offset++) {
            week.add(fromShortTerm(today.plusDays(offset), shortTerm));
        }

        for (int offset = 4; offset <= 6; offset++) {
            week.add(fromMid(today.plusDays(offset), midBaseDate, midTa, midLand));
        }

        return week;
    }

    /**
     * 단기예보에서 하루 요약 - TMN/TMX 우선, 없으면 시간별 TMP의 최소/최대로 폴백
     * */
    private DailyWeather fromShortTerm(LocalDate date, List<ShortTermItem> items) {
        String ymd = date.format(YMD);
        List<ShortTermItem> daily = items.stream()
                .filter(item -> ymd.equals(item.fcstDate()))
                .toList();

        Integer min = categoryValue(daily, "TMN");
        Integer max = categoryValue(daily, "TMX");
        List<Integer> temps = daily.stream()
                .filter(item -> "TMP".equals(item.category()))
                .map(item -> parse(item.fcstValue()))
                .filter(Objects::nonNull)
                .toList();
        if (min == null && !temps.isEmpty()) min = temps.stream().min(Integer::compare).get();
        if (max == null && !temps.isEmpty()) max = temps.stream().max(Integer::compare).get();

        Integer rainProb = daily.stream()
                .filter(item -> "POP".equals(item.category()))
                .map(item -> parse(item.fcstValue()))
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);

        return new DailyWeather(date, min, max, skyText(daily), rainProb);
    }

    /**
     * 중기예보에서 하루 요약 - 발표일 기준 몇 번째 날인지로 필드를 고른다 (폴백 시 어긋남 방지)
     * */
    private DailyWeather fromMid(LocalDate date, LocalDate midBaseDate, MidTaItem ta, MidLandItem land) {
        int day = (int) (date.toEpochDay() - midBaseDate.toEpochDay());
        return switch (day) {
            case 4 -> new DailyWeather(date, ta.taMin4(), ta.taMax4(), land.wf4Am(), land.rnSt4Am());
            case 5 -> new DailyWeather(date, ta.taMin5(), ta.taMax5(), land.wf5Am(), land.rnSt5Am());
            case 6 -> new DailyWeather(date, ta.taMin6(), ta.taMax6(), land.wf6Am(), land.rnSt6Am());
            case 7 -> new DailyWeather(date, ta.taMin7(), ta.taMax7(), land.wf7Am(), land.rnSt7Am());
            default -> new DailyWeather(date, null, null, null, null);
        };
    }

    /**
     * 정오 SKY/PTY를 하루 대표값으로 - 강수(PTY)가 있으면 우선
     * */
    private String skyText(List<ShortTermItem> daily) {
        String pty = at(daily, "PTY", "1200");
        if (pty != null && !"0".equals(pty)) {
            return ("3".equals(pty) || "7".equals(pty)) ? "눈" : "비";
        }
        String sky = at(daily, "SKY", "1200");
        if (sky == null) return null;
        return switch (sky) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> "흐림";
        };
    }

    private String at(List<ShortTermItem> daily, String category, String time) {
        return daily.stream()
                .filter(item -> category.equals(item.category()))
                .filter(item -> time.equals(item.fcstTime()))
                .map(ShortTermItem::fcstValue)
                .findFirst()
                .orElseGet(() -> daily.stream()
                        .filter(item -> category.equals(item.category()))
                        .map(ShortTermItem::fcstValue)
                        .findFirst()
                        .orElse(null));
    }

    private Integer categoryValue(List<ShortTermItem> daily, String category) {
        return daily.stream()
                .filter(item -> category.equals(item.category()))
                .map(item -> parse(item.fcstValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * "21.0" 같은 소수 문자열도 옴 - 반올림 정수로 통일
     * */
    private Integer parse(String value) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

}
