package com.example.hangat.map.hiddengem;

import com.example.hangat.common.util.DateTimes;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceSourceMapping;
import com.example.hangat.map.repository.CongestionForecastRepository;
import com.example.hangat.map.repository.PlaceSourceMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 숨은 명소 점수·판정을 places 에 기록한다 (#숨은 명소). 규칙은 {@link HiddenGemRule} 한 곳에 있다.
 *
 * <p>언제 도는가: 혼잡(집중률) 적재 직후. "유명도 하위"가 최신 집중률 발표분의 집계 대상 여부로 정해지므로
 * 그 발표분이 없으면 판정할 수 없다 - 그때는 아무것도 바꾸지 않고 {@code skipped} 로 알린다.
 * 없는 값을 기준으로 전부 "숨은 명소"로 만드는 일이 없게.
 *
 * <p>멱등: 같은 날 다시 돌려도 같은 결과를 덮어쓴다. 판정과 무관하게 후보 전부에 점수·버전·계산 시각을 남긴다.
 */
@Service
public class HiddenGemScoringService {

    private static final Logger log = LoggerFactory.getLogger(HiddenGemScoringService.class);

    static final String KTO = "KTO";

    private final HiddenGemRule rule;
    private final PlaceSourceMappingRepository mappingRepository;
    private final CongestionForecastRepository congestionRepository;

    public HiddenGemScoringService(HiddenGemRule rule,
                                   PlaceSourceMappingRepository mappingRepository,
                                   CongestionForecastRepository congestionRepository) {
        this.rule = rule;
        this.mappingRepository = mappingRepository;
        this.congestionRepository = congestionRepository;
    }

    /**
     * @param version   적용한 규칙 버전
     * @param skipped   최신 집중률 발표분이 없어 판정을 건너뛰었는가(아무것도 바꾸지 않았다)
     * @param evaluated 점수를 기록한 KTO 장소 수
     * @param hiddenGems 이번 판정으로 숨은 명소가 된 수
     * @param famous    집계 대상(주요 관광지)이라 제외된 수
     * @param baseAt    유명도 판정에 쓴 집중률 발표분
     */
    public record HiddenGemScoringResult(String version, boolean skipped, int evaluated, int hiddenGems,
                                         int famous, int closed, LocalDateTime baseAt) {
    }

    @Transactional
    public HiddenGemScoringResult score() {
        LocalDateTime baseAt = congestionRepository.findLatestBaseAt().orElse(null);
        if (baseAt == null) {
            log.warn("숨은 명소 판정 건너뜀 - 집중률 발표분이 없어 유명도를 가릴 수 없다");
            return new HiddenGemScoringResult(HiddenGemRule.VERSION, true, 0, 0, 0, 0, null);
        }
        Set<Long> famousIds = new HashSet<>(congestionRepository.findPlaceIdsOfVersion(baseAt));
        List<PlaceSourceMapping> mappings = mappingRepository.findAllBySourceCodeWithPlace(KTO);
        LocalDateTime now = DateTimes.nowUtc();

        int evaluated = 0;
        int gems = 0;
        int famous = 0;
        int closed = 0;
        for (PlaceSourceMapping mapping : mappings) {
            if (!mapping.isActive()) continue;   // 출처에서 사라진 매핑은 판정하지 않는다
            Place place = mapping.getPlace();
            HiddenGemRule.Verdict verdict = rule.evaluate(place, famousIds.contains(place.getId()));
            place.updateHiddenGem(verdict.score(), verdict.hiddenGem(), HiddenGemRule.VERSION, now);
            evaluated++;
            if (verdict.hiddenGem()) gems++;
            if ("famous".equals(verdict.reason())) famous++;
            if ("closed".equals(verdict.reason())) closed++;
        }
        HiddenGemScoringResult result = new HiddenGemScoringResult(
                HiddenGemRule.VERSION, false, evaluated, gems, famous, closed, baseAt);
        log.info("숨은 명소 판정 완료 {}", result);
        return result;
    }
}
