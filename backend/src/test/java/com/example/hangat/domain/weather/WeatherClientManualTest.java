package com.example.hangat.domain.weather;

import com.example.hangat.config.RestClientConfig;
import com.example.hangat.domain.weather.model.MidLandItem;
import com.example.hangat.domain.weather.model.MidTaItem;
import com.example.hangat.domain.weather.model.ShortTermItem;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기상청 실호출 수동 테스트 - 로컬에서만 실행 (.env의 SERVICE_KEY 필요)
 * CI에서 돌면 안 되므로 평소에는 @Disabled를 붙여둔다.
 */
@Disabled("기상청 실호출 - 로컬에서 @Disabled 잠시 풀고 수동 실행 (마지막 검증: 2026-08-22 성공)")
class WeatherClientManualTest {

    @Test
    void 기상청_3종_실호출() throws IOException {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("SERVICE_KEY", System.getenv("SERVICE_KEY"));
        assertThat(key).as(".env의 SERVICE_KEY").isNotBlank();

        WeatherProperties properties = new WeatherProperties("https://apis.data.go.kr/1360000", key);
        RestClient restClient = new RestClientConfig().weatherRestClient(properties);
        WeatherClient client = new WeatherClient(restClient, properties);

        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        List<ShortTermItem> shortTerm;
        try {
            shortTerm = client.fetchShortTerm(today, "0500");
        } catch (com.example.hangat.common.exception.BaseException e) {
            Files.writeString(Path.of("build/weather-manual-result.txt"),
                    "실패 원인: " + e.getResult());
            throw e;
        }
        MidTaItem midTa = client.fetchMidTemperature(today + "0600");
        MidLandItem midLand = client.fetchMidLand(today + "0600");

        assertThat(shortTerm).isNotEmpty();
        assertThat(shortTerm).anyMatch(item -> "TMX".equals(item.category()));
        assertThat(midTa.taMin4()).as("중기기온 +4일 최저").isNotNull();
        assertThat(midLand.wf4Am()).as("중기육상 +4일 오전 날씨").isNotBlank();

        String summary = """
                단기예보 행 수: %d (첫 행: %s)
                중기기온: +3일 %s~%s / +4일 %s~%s / +5일 %s~%s / +6일 %s~%s / +7일 %s~%s
                중기육상: +3일 %s(%s%%) / +4일 %s(%s%%) / +5일 %s(%s%%) / +6일 %s(%s%%) / +7일 %s(%s%%)
                """.formatted(
                shortTerm.size(), shortTerm.get(0),
                midTa.taMin3(), midTa.taMax3(), midTa.taMin4(), midTa.taMax4(),
                midTa.taMin5(), midTa.taMax5(), midTa.taMin6(), midTa.taMax6(),
                midTa.taMin7(), midTa.taMax7(),
                midLand.wf3Am(), midLand.rnSt3Am(), midLand.wf4Am(), midLand.rnSt4Am(),
                midLand.wf5Am(), midLand.rnSt5Am(), midLand.wf6Am(), midLand.rnSt6Am(),
                midLand.wf7Am(), midLand.rnSt7Am());
        Files.writeString(Path.of("build/weather-manual-result.txt"), summary);
    }
}
