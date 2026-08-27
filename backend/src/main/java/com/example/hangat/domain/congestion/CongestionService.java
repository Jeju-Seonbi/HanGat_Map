package com.example.hangat.domain.congestion;

import com.example.hangat.domain.congestion.model.CongestionForecast;
import com.example.hangat.domain.congestion.model.CongestionLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 혼잡 공통 코어 - 메인 추천·코스 생성·대안 스왑이 모두 이 서비스를 통해 등급을 본다.
 * 임계값은 휴리스틱(캘리브레이션 예정)이므로 반드시 아래 상수로만 관리한다.
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

    /** 그날 예보 전체를 placeId → 집중률 맵으로 (예보 없는 날짜면 빈 맵) */
    public Map<Long, Double> ratesFor(LocalDate date) {
        return repository.findByBaseDate(date).stream()
                .collect(Collectors.toMap(
                        forecast -> forecast.getPlace().getId(),
                        CongestionForecast::getRate));
    }
}
