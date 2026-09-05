package com.example.hangat.domain.weather;

import com.example.hangat.domain.weather.model.DailyWeather;
import com.example.hangat.domain.weather.model.MidLandItem;
import com.example.hangat.domain.weather.model.MidTaItem;
import com.example.hangat.domain.weather.model.ShortTermItem;
import com.example.hangat.domain.weather.model.entity.WeatherForecast;
import com.example.hangat.domain.weather.model.enums.PrecipitationType;
import com.example.hangat.domain.weather.repository.WeatherForecastRepository;
import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.DataSourceRepository;
import com.example.hangat.map.repository.RegionRepository;
import com.example.hangat.map.service.PlaceNameNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 주간 날씨의 DB 우선 경로 - 적재된 발표분이 있으면 기상청을 부르지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeatherServiceStoreReadTest {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final LocalDateTime BASE_OLD = LocalDateTime.of(2026, 9, 1, 20, 0);
    private static final LocalDateTime BASE_NEW = LocalDateTime.of(2026, 9, 2, 20, 0);

    @Autowired WeatherService service;
    @Autowired WeatherForecastRepository repository;
    @Autowired RegionRepository regionRepository;
    @Autowired DataSourceRepository dataSourceRepository;
    @MockitoBean WeatherClient client;

    private final LocalDate today = LocalDate.now(KmaIssueTimes.KST);
    private Region north;
    private Region east;
    private DataSource kmaShort;

    @BeforeEach
    void setUp() {
        north = regionRepository.findByCode("NORTH").orElseThrow();
        east = regionRepository.findByCode("EAST").orElseThrow();
        kmaShort = dataSourceRepository.findById("KMA_SHORT").orElseThrow();
    }

    @Test
    @DisplayName("적재된 7일이 있으면 그대로 돌려주고 기상청은 부르지 않는다 - 같은 날짜는 최신 발표분이 이긴다")
    void storedWeekWinsWithoutLiveCall() {
        for (int offset = 0; offset < 7; offset++) {
            store(north, today.plusDays(offset), BASE_NEW, "맑음", 20 + offset, 28 + offset, 10);
        }
        store(north, today, BASE_OLD, "비", 18, 24, 80);   // 어제 발표분 - 오늘 날짜에 대해 더 오래된 버전

        List<DailyWeather> week = service.getWeeklyForecast();

        assertThat(week).hasSize(7);
        assertThat(week.get(0).date()).isEqualTo(today);
        assertThat(week.get(0).sky()).isEqualTo("맑음");
        assertThat(week.get(0).minTemp()).isEqualTo(20);
        assertThat(week.get(0).rainProb()).isEqualTo(10);
        assertThat(week.get(6).date()).isEqualTo(today.plusDays(6));
        assertThat(week.get(6).maxTemp()).isEqualTo(34);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("일부 날짜만 있으면 빈 날짜는 null - 라이브로 메우거나 지어내지 않는다")
    void missingDatesStayNull() {
        store(north, today, BASE_NEW, "흐림", 21, 27, 30);
        store(north, today.plusDays(1), BASE_NEW, "맑음", 22, 29, 0);

        List<DailyWeather> week = service.getWeeklyForecast();

        assertThat(week).hasSize(7);
        assertThat(week.get(1).sky()).isEqualTo("맑음");
        for (int offset = 2; offset < 7; offset++) {
            DailyWeather day = week.get(offset);
            assertThat(day.date()).isEqualTo(today.plusDays(offset));
            assertThat(day.minTemp()).isNull();
            assertThat(day.maxTemp()).isNull();
            assertThat(day.sky()).isNull();
            assertThat(day.rainProb()).isNull();
        }
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("기준 권역(북부)에 한 날짜도 없으면 라이브 호출로 폴백한다 - 다른 권역 행은 메인에 새지 않는다")
    void fallsBackToLiveWhenMainRegionIsEmpty() {
        store(east, today, BASE_NEW, "맑음", 25, 30, 0);
        given(client.fetchShortTerm(anyString(), anyString())).willReturn(liveShortTerm());
        given(client.fetchMidTemperature(anyString())).willReturn(new MidTaItem(null, null, 24, 31, 25, 32, 26, 33, 27, 34));
        given(client.fetchMidLand(anyString())).willReturn(new MidLandItem(null, "구름많음", "흐림", "맑음", "흐림", null, 30, 40, 60, 70));

        List<DailyWeather> week = service.getWeeklyForecast();

        assertThat(week).hasSize(7);
        assertThat(week.get(0).minTemp()).isEqualTo(20);
        assertThat(week.get(0).sky()).isEqualTo("구름많음");
        assertThat(week.get(4).minTemp()).isEqualTo(24);
        verify(client).fetchShortTerm(today.format(YMD), "0500");
    }

    private void store(Region region, LocalDate day, LocalDateTime baseAtUtc,
                       String sky, int min, int max, int rainProb) {
        repository.saveAndFlush(WeatherForecast.daily(region, kmaShort,
                PlaceNameNormalizer.jejuDayToUtc(day), baseAtUtc,
                sky, PrecipitationType.fromForecastText(sky), min, max, rainProb));
    }

    private List<ShortTermItem> liveShortTerm() {
        List<ShortTermItem> items = new java.util.ArrayList<>();
        for (int d = 0; d <= 3; d++) {
            String date = today.plusDays(d).format(YMD);
            items.add(new ShortTermItem("TMN", date, "0600", String.valueOf(20 + d)));
            items.add(new ShortTermItem("TMX", date, "1500", String.valueOf(30 + d)));
            items.add(new ShortTermItem("SKY", date, "1200", "3"));
            items.add(new ShortTermItem("PTY", date, "1200", "0"));
            items.add(new ShortTermItem("POP", date, "1200", "20"));
        }
        return items;
    }
}
