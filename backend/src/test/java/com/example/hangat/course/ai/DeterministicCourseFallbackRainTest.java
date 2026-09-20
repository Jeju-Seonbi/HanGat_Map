package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.RequiredCandidateConstraintDto;
import com.example.hangat.course.ai.CourseAiInputDto.WeatherAiFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.WeatherAiFactSetDto;
import com.example.hangat.course.model.Transport;
import com.example.hangat.course.service.RainyDayRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM이 실패해도 우천 동작은 배치 코스와 같아야 한다 - 비 예보일에는 실내 후보를 먼저, 맑은 날은 기존 순서 그대로,
 * WANT·고정 일정은 규칙보다 우선.
 */
class DeterministicCourseFallbackRainTest {

    private static final LocalDate RAINY = LocalDate.of(2026, 9, 20);
    private static final LocalDate SUNNY = LocalDate.of(2026, 9, 21);
    private static final String EAST = "db-weather-EAST";

    private final DeterministicCourseFallback fallback = new DeterministicCourseFallback();

    /** 후보 여섯 곳 - id 순서가 곧 기본 순서(스타일·혼잡 동률). 실내 셋, 실외 셋이 번갈아 온다. */
    private static final List<CandidateFactDto> CANDIDATES = List.of(
            candidate("a", "성산일출봉", EAST),
            candidate("b", "제주민속자연사박물관", EAST),
            candidate("c", "협재해수욕장", EAST),
            candidate("d", "빛의 벙커 전시관", EAST),
            candidate("e", "천지연폭포", EAST),
            candidate("f", "산방산탄산온천", EAST));

    @Test
    void 비_예보일에는_실내_후보를_먼저_담고_사유에_비_예보를_적는다() {
        CourseAiResultDto result = fallback.generate(input(CANDIDATES, List.of(), weather(Map.of(RAINY, 80, SUNNY, 10))));

        Map<LocalDate, List<String>> byDate = names(result);
        // 비 오는 날: 실내 셋(b, d, f)이 먼저 - 하루 정원 3이라 전부 실내
        assertThat(byDate.get(RAINY)).containsExactly("b", "d", "f");
        assertThat(byDate.get(SUNNY)).containsExactly("a", "c", "e");
        result.days().stream().filter(day -> day.date().equals(RAINY)).flatMap(day -> day.items().stream())
                .forEach(item -> assertThat(item.recommendationReason()).isEqualTo(RainyDayRule.INDOOR_REASON));
        result.days().stream().filter(day -> day.date().equals(SUNNY)).flatMap(day -> day.items().stream())
                .forEach(item -> assertThat(item.recommendationReason()).isNotEqualTo(RainyDayRule.INDOOR_REASON));
    }

    @Test
    void 비_예보일이_둘째_날이어도_맑은_첫날이_실내_후보를_먼저_써_버리지_않는다() {
        // 날짜는 덜 찬 날부터 채우므로 첫날(맑음)이 먼저 고른다 - 실내 후보를 아껴 두지 않으면 비 오는 둘째 날에 실외만 남는다
        CourseAiResultDto result = fallback.generate(input(CANDIDATES, List.of(), weather(Map.of(RAINY, 10, SUNNY, 80))));

        Map<LocalDate, List<String>> byDate = names(result);
        assertThat(byDate.get(SUNNY)).containsExactly("b", "d", "f");   // 둘째 날이 비 - 실내 셋
        assertThat(byDate.get(RAINY)).containsExactly("a", "c", "e");   // 첫날은 맑음 - 실외부터
    }

    @Test
    void 맑은_날만_있으면_날씨가_없을_때와_결과가_같다() {
        CourseAiResultDto withWeather = fallback.generate(input(CANDIDATES, List.of(), weather(Map.of(RAINY, 10, SUNNY, 20))));
        CourseAiResultDto withoutWeather = fallback.generate(input(CANDIDATES, List.of(), List.of()));

        assertThat(withWeather).isEqualTo(withoutWeather);
        // 기존 순서: 날짜를 번갈아 채우므로 a·c·e / b·d·f
        assertThat(names(withWeather).get(RAINY)).containsExactly("a", "c", "e");
    }

    @Test
    void 임계값_바로_아래는_비_예보일이_아니다() {
        CourseAiResultDto result = fallback.generate(input(CANDIDATES, List.of(),
                weather(Map.of(RAINY, RainyDayRule.PROB_FROM - 1, SUNNY, 0))));

        assertThat(names(result).get(RAINY)).containsExactly("a", "c", "e");
    }

    @Test
    void 고정_일정과_WANT는_우천_규칙보다_우선한다() {
        // 실외인 성산일출봉을 비 오는 날 10시에 고정 - 규칙이 이를 밀어내면 안 된다
        List<RequiredCandidateConstraintDto> required = List.of(
                new RequiredCandidateConstraintDto("a", RAINY, LocalTime.of(10, 0)));

        CourseAiResultDto result = fallback.generate(input(CANDIDATES, required, weather(Map.of(RAINY, 90, SUNNY, 10))));

        List<String> rainyDay = names(result).get(RAINY);
        assertThat(rainyDay.get(0)).isEqualTo("a");
        assertThat(rainyDay.subList(1, rainyDay.size())).containsExactly("b", "d");   // 남은 두 자리는 실내 우선
    }

    @Test
    void 날씨_세트가_없는_후보는_비_예보일로_보지_않는다() {
        List<CandidateFactDto> noWeather = List.of(
                candidate("a", "성산일출봉", null),
                candidate("b", "제주민속자연사박물관", null),
                candidate("c", "협재해수욕장", null),
                candidate("d", "빛의 벙커 전시관", null));

        CourseAiResultDto result = fallback.generate(input(noWeather, List.of(), weather(Map.of(RAINY, 95, SUNNY, 95))));

        // 세트를 참조하지 않으니 기존 순서 그대로 - 모르는 날씨로 실내를 우선하지 않는다
        assertThat(names(result).get(RAINY)).containsExactly("a", "c");
        assertThat(names(result).get(SUNNY)).containsExactly("b", "d");
    }

    private static CandidateFactDto candidate(String id, String name, String weatherFactSetId) {
        return new CandidateFactDto(id, name, "EAST", "TOURIST", List.of(), List.of(), weatherFactSetId, null);
    }

    private static List<WeatherAiFactSetDto> weather(Map<LocalDate, Integer> probabilityByDate) {
        return List.of(new WeatherAiFactSetDto(EAST, probabilityByDate.entrySet().stream()
                .map(entry -> new WeatherAiFactDto(entry.getKey(), null, null, entry.getValue(), "0", "1", null))
                .toList()));
    }

    private static CourseAiInputDto input(List<CandidateFactDto> candidates,
                                          List<RequiredCandidateConstraintDto> required,
                                          List<WeatherAiFactSetDto> weatherFactSets) {
        return new CourseAiInputDto("2.0",
                new CourseAiInputDto.TripConstraintDto(RAINY, SUNNY, Transport.RENTAL_CAR),
                new CourseAiInputDto.SoftPreferencesDto(List.of("EAST"), List.of()),
                new CourseAiInputDto.HardConstraintsDto(required), null,
                candidates, weatherFactSets, List.of(), null);
    }

    /** 날짜별 candidateId 목록(시작 시간 순). */
    private static Map<LocalDate, List<String>> names(CourseAiResultDto result) {
        return result.days().stream().collect(java.util.stream.Collectors.toMap(
                CourseAiResultDto.DayDto::date,
                day -> day.items().stream().map(CourseAiResultDto.ItemDto::candidateId).toList()));
    }
}
