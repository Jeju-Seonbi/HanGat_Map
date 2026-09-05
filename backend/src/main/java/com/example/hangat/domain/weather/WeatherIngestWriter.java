package com.example.hangat.domain.weather;

import com.example.hangat.domain.weather.model.entity.WeatherForecast;
import com.example.hangat.domain.weather.model.enums.WeatherGranularity;
import com.example.hangat.domain.weather.repository.WeatherForecastRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 날씨 예보 발표 버전 저장 전담.
 *
 * <p>별도 클래스인 이유는 {@code CongestionIngestWriter}와 같다 - 같은 클래스 안에서 {@code @Transactional}
 * 메서드를 부르면 프록시를 안 거쳐 트랜잭션이 안 걸린다.
 *
 * <p>한 발표 버전은 많아야 권역 4 × 날짜 4 = 16행이라 청크가 필요 없다. 대신 <b>지우기와 넣기를 한 트랜잭션</b>으로
 * 묶는다 - 넣다가 실패하면 지운 것도 되돌아가 기존 버전이 남는다.
 */
@Component
public class WeatherIngestWriter {

    private static final Logger log = LoggerFactory.getLogger(WeatherIngestWriter.class);

    private final WeatherForecastRepository repository;

    public WeatherIngestWriter(WeatherForecastRepository repository) {
        this.repository = repository;
    }

    public record Replaced(int removed, int saved) {
    }

    /**
     * 같은 발표 버전(base_at, DAILY)을 지우고 다시 넣는다. 다른 버전은 건드리지 않는다 -
     * "어제 발표를 오늘 발표로 덮지 말라"는 명세서 규칙은 그대로다.
     * course_items 스냅숏 FK가 ON DELETE SET NULL이라 코스가 이 삭제를 막지 않는다.
     */
    @Transactional
    public Replaced replaceVersion(LocalDateTime baseAtUtc, List<WeatherForecast> rows) {
        int removed = repository.deleteVersion(baseAtUtc, WeatherGranularity.DAILY);
        if (removed > 0) {
            log.warn("같은 발표 버전 {}행을 지우고 다시 넣는다 baseAt(UTC)={} (다른 버전은 그대로 둔다)",
                    removed, baseAtUtc);
        }
        int saved = repository.saveAll(rows).size();
        repository.flush();
        return new Replaced(removed, saved);
    }
}
