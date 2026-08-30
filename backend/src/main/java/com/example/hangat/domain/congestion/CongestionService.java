package com.example.hangat.domain.congestion;

import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.map.repository.CongestionForecastRepository;
import com.example.hangat.map.service.PlaceNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 혼잡 공통 코어 - 메인 추천·코스 생성·대안 스왑이 모두 이 서비스를 통해 등급을 본다.
 * 임계값은 휴리스틱(캘리브레이션 예정)이므로 반드시 아래 상수로만 관리한다.
 *
 * <p><b>2026-08-24 통합</b>: 데이터 계층을 {@code map} 도메인으로 옮겼다.
 * 원래 이 패키지에 {@code Place}·{@code CongestionForecast}가 따로 있었는데,
 * 지도 담당이 명세서(9.0/16.0) 기준으로 만든 것과 <b>엔티티 이름이 같아 빈 등록이 충돌</b>했다.
 * 등급 변환(이 클래스의 본체)은 그대로 두고 조회만 갈아끼운 것이라 호출부는 영향이 없다.
 */
@Service
@Transactional(readOnly = true)
public class CongestionService {

    /** 등급 임계값 - 프론트 utils/congestion.ts와 동일해야 한다 (여유 <40 / 보통 <70 / 혼잡 <80 / 매우혼잡 >=80) */
    private static final double MODERATE_FROM = 40.0;
    private static final double CROWDED_FROM = 70.0;
    private static final double VERY_CROWDED_FROM = 80.0;

    private final CongestionForecastRepository repository;

    public CongestionService(CongestionForecastRepository repository) {
        this.repository = repository;
    }

    public CongestionLevel levelOf(double rate) {
        if (rate < MODERATE_FROM) return CongestionLevel.RELAXED;
        if (rate < CROWDED_FROM) return CongestionLevel.MODERATE;
        if (rate < VERY_CROWDED_FROM) return CongestionLevel.CROWDED;
        return CongestionLevel.VERY_CROWDED;
    }

    /**
     * 그날 예보 전체를 placeId → 집중률 맵으로 (예보 없는 날짜면 빈 맵).
     *
     * <p>두 가지를 맞춰 준다.
     * <ul>
     *   <li><b>발표 버전 고정</b>: 예보는 덮어쓰지 않고 발표할 때마다 쌓이므로(명세서 16.0),
     *       최신 {@code base_at}만 골라야 한 장소가 두 번 나오지 않는다</li>
     *   <li><b>시각 변환</b>: {@code forecast_at}은 UTC로 저장돼 있다. 제주 기준 날짜를
     *       그대로 넘기면 9시간 어긋나 하루가 통째로 빈다</li>
     * </ul>
     */
    public Map<Long, Double> ratesFor(LocalDate date) {
        Optional<LocalDateTime> latestBase = repository.findLatestBaseAt();
        if (latestBase.isEmpty()) {
            return Map.of();
        }
        return repository.findByBaseAtAndForecastAt(latestBase.get(), PlaceNameNormalizer.jejuDayToUtc(date))
                .stream()
                .collect(Collectors.toMap(
                        forecast -> forecast.getPlace().getId(),
                        forecast -> forecast.getRate().doubleValue(),
                        (a, b) -> a));
    }
}
