package com.example.hangat.domain.main;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.domain.congestion.CongestionService;
import com.example.hangat.domain.congestion.model.CongestionLevel;
import com.example.hangat.domain.main.model.CalmPlaceResponse;
import com.example.hangat.domain.place.PlaceRepository;
import com.example.hangat.domain.place.model.Place;
import com.example.hangat.domain.place.model.PlaceCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 메인 페이지 추천 (담당: 정동현) - 오늘의 한산 장소 (MAIN_001) */
@Service
@Transactional(readOnly = true)
public class MainService {

    private final PlaceRepository placeRepository;
    private final CongestionService congestionService;

    public MainService(PlaceRepository placeRepository, CongestionService congestionService) {
        this.placeRepository = placeRepository;
        this.congestionService = congestionService;
    }

    /**
     * 해당 날짜 예보 기준 집중률 낮은 순 관광지 상위 N.
     * 혼잡(CROWDED) 이상은 하드 컷 - 추천 목록에 아예 올리지 않는다.
     */
    public List<CalmPlaceResponse> calmPlaces(LocalDate date, int limit) {
        Map<Long, Double> rates = congestionService.ratesFor(date);
        if (rates.isEmpty()) {
            throw new BaseException(BaseResponseStatus.CONGESTION_NOT_FOUND, date.toString());
        }

        return placeRepository.findAllById(rates.keySet()).stream()
                .filter(place -> place.getCategory() == PlaceCategory.TOURIST)
                .map(place -> toResponse(place, rates.get(place.getId())))
                .filter(response -> response.level() == CongestionLevel.RELAXED
                        || response.level() == CongestionLevel.MODERATE)
                .sorted(Comparator.comparingDouble(CalmPlaceResponse::rate))
                .limit(limit)
                .toList();
    }

    private CalmPlaceResponse toResponse(Place place, double rate) {
        CongestionLevel level = congestionService.levelOf(rate);
        return CalmPlaceResponse.of(place, rate, level, reasonFor(place, level));
    }

    /** 추천 근거 칩 - 우선순위: 검증가 > 숨은 명소 > 예보 여유 (프론트 문구와 동일 규칙) */
    private String reasonFor(Place place, CongestionLevel level) {
        if (place.isGoodPriceStore()) return "착한가격업소 검증가";
        if (place.isHiddenGem()) return "덜 알려진 숨은 명소";
        if (level == CongestionLevel.RELAXED) return "이 날짜 혼잡 예보가 여유예요";
        return "인기 명소보다 한산한 편이에요";
    }
}
