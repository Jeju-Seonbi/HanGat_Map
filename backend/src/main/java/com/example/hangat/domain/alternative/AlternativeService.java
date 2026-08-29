package com.example.hangat.domain.alternative;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.geo.GeoService;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.domain.alternative.model.AlternativePlaceResponse;
import com.example.hangat.domain.congestion.CongestionService;
import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 과밀 스팟 대안 제시 (담당: 정동현) - #과밀지역우회
 * 규칙: 같은 카테고리 + 반경 내 + 그날 예보 존재 + 혼잡 미만, 집중률 낮은 순 상위 N.
 * [바운딩 박스 선필터(DB) → 하버사인 정밀 컷(자바)] 2단계 - GeoService 주석 참고.
 */
@Service
@Transactional(readOnly = true)
public class AlternativeService {

    /** 대안 탐색 반경(km) - 데모 해보고 튜닝할 값이라 상수로 분리 */
    private static final double RADIUS_KM = 15.0;

    private final PlaceRepository placeRepository;
    private final CongestionService congestionService;
    private final GeoService geoService;

    public AlternativeService(PlaceRepository placeRepository,
                              CongestionService congestionService,
                              GeoService geoService) {
        this.placeRepository = placeRepository;
        this.congestionService = congestionService;
        this.geoService = geoService;
    }

    public List<AlternativePlaceResponse> alternatives(Long placeId, LocalDate date,
                                                       Set<Long> excludeIds, int limit) {
        Place base = placeRepository.findById(placeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.PLACE_NOT_FOUND, placeId));

        Map<Long, Double> rates = congestionService.ratesFor(date);
        if (rates.isEmpty()) {
            throw new BaseException(BaseResponseStatus.CONGESTION_NOT_FOUND, date.toString());
        }

        GeoService.BoundingBox box = geoService.boxAround(
                base.getLatitude().doubleValue(), base.getLongitude().doubleValue(), RADIUS_KM);

        return placeRepository.findCandidatesInBox(
                        base.getPrimaryCategory().getCode(),
                        BigDecimal.valueOf(box.minLat()), BigDecimal.valueOf(box.maxLat()),
                        BigDecimal.valueOf(box.minLng()), BigDecimal.valueOf(box.maxLng()))
                .stream()
                .filter(place -> !place.getId().equals(base.getId()))
                .filter(place -> !excludeIds.contains(place.getId()))
                .filter(place -> rates.containsKey(place.getId()))
                .map(place -> toResponse(base, place, rates.get(place.getId())))
                .filter(response -> response.level() == CongestionLevel.RELAXED
                        || response.level() == CongestionLevel.MODERATE)
                .filter(response -> response.distanceKm() <= RADIUS_KM)
                .sorted(Comparator.comparingDouble(AlternativePlaceResponse::rate))
                .limit(limit)
                .toList();
    }

    private AlternativePlaceResponse toResponse(Place base, Place candidate, double rate) {
        CongestionLevel level = congestionService.levelOf(rate);
        double distance = geoService.distanceKm(
                base.getLatitude(), base.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude());
        return AlternativePlaceResponse.of(candidate, rate, level, distance, reasonFor(candidate, level));
    }

    /** 추천 근거 - MainService와 같은 우선순위 (검증가 > 숨은 명소 > 예보 여유) */
    private String reasonFor(Place place, CongestionLevel level) {
        if (place.isGoodPrice()) return "착한가격업소 검증가";
        if (place.isHiddenGem()) return "덜 알려진 숨은 명소";
        if (level == CongestionLevel.RELAXED) return "이 날짜 혼잡 예보가 여유예요";
        return "인기 명소보다 한산한 편이에요";
    }
}
