package com.example.hangat.map.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이름 정규화·날짜 변환 규칙 검증.
 *
 * <p><b>둘 다 틀려도 프로그램이 멀쩡히 도는 종류의 규칙이다.</b>
 * <ul>
 *   <li>정규화가 어긋나면 매칭률이 0%가 되는데 적재는 성공으로 끝난다 -
 *       화면에서 혼잡 정보가 사라진 것으로만 드러난다</li>
 *   <li>시간대 변환이 틀리면 캘린더 전체가 하루 밀리는데, 값이 그럴듯해서 눈으로는 못 잡는다</li>
 * </ul>
 */
class PlaceNameNormalizerTest {

    @Test
    void 표기_흔들림을_흡수한다() {
        // 집중률과 KTO가 띄어쓰기·가운뎃점을 다르게 쓰는 경우
        assertThat(PlaceNameNormalizer.normalize("성산 일출봉"))
                .isEqualTo(PlaceNameNormalizer.normalize("성산일출봉"));
        assertThat(PlaceNameNormalizer.normalize("오설록 티 뮤지엄"))
                .isEqualTo(PlaceNameNormalizer.normalize("오설록티뮤지엄"));
        assertThat(PlaceNameNormalizer.normalize("수목원ㆍ정원")).isEqualTo("수목원ㆍ정원");
        assertThat(PlaceNameNormalizer.normalize("해변. 해수욕장")).isEqualTo("해변.해수욕장");
        assertThat(PlaceNameNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void 괄호_안은_남겨서_다른_장소가_합쳐지지_않게_한다() {
        // 괄호가 유일한 구분자인 장소들. 괄호 안까지 지우면 서로 다른 오름이 하나가 된다
        assertThat(PlaceNameNormalizer.normalize("열안지오름(봉개동)"))
                .isNotEqualTo(PlaceNameNormalizer.normalize("열안지오름(오라동)"));
        // 괄호 기호 자체는 지운다 - 표기 흔들림은 흡수해야 하므로
        assertThat(PlaceNameNormalizer.normalize("열안지오름(봉개동)")).isEqualTo("열안지오름봉개동");
    }

    @Test
    void 제주_기준일은_UTC로_9시간_당겨_저장된다() {
        LocalDateTime utc = PlaceNameNormalizer.jejuDayToUtc(LocalDate.of(2026, 8, 23));

        // 제주 8/23 00:00 == UTC 8/22 15:00. 하루 밀림을 잡는 유일한 장치다
        assertThat(utc).isEqualTo(LocalDateTime.of(2026, 8, 22, 15, 0));
    }

    @Test
    void 저장한_날짜를_되돌리면_원래_기준일이_나온다() {
        // 22일치 전부 왕복시켜 본다 - 월말·월초 경계에서만 틀어지는 실수를 잡으려면 한 건으로는 부족하다
        LocalDate start = LocalDate.of(2026, 8, 23);
        for (int i = 0; i < 22; i++) {
            LocalDate day = start.plusDays(i);
            assertThat(PlaceNameNormalizer.utcToJejuDay(PlaceNameNormalizer.jejuDayToUtc(day)))
                    .isEqualTo(day);
        }
    }

    @Test
    void 연말_경계에서도_날짜가_밀리지_않는다() {
        LocalDate 마지막날 = LocalDate.of(2026, 12, 31);
        LocalDateTime utc = PlaceNameNormalizer.jejuDayToUtc(마지막날);

        assertThat(utc).isEqualTo(LocalDateTime.of(2026, 12, 30, 15, 0));
        assertThat(PlaceNameNormalizer.utcToJejuDay(utc)).isEqualTo(마지막날);
    }
}
