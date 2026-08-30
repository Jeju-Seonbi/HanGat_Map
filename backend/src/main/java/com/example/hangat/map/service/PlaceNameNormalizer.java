package com.example.hangat.map.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 장소 이름 정규화와 제주 기준일 변환 - 적재 배치들이 공유하는 규칙.
 *
 * <p><b>왜 공용으로 뺐나</b>: 이름 정규화는 {@code places.normalized_name}을 <b>쓰는 쪽</b>
 * (집중률 매칭)과 <b>만드는 쪽</b>(KTO 적재)에 각각 필요하다. 두 곳에 복붙해 두면
 * 한쪽만 고쳤을 때 <b>매칭이 조용히 전부 실패</b>한다 - 에러도 안 나고 적재도 성공하며,
 * 화면에서 혼잡 정보가 사라진 것으로만 드러난다.
 */
public final class PlaceNameNormalizer {

    /** 제주 현지 시간대. 데이터가 전부 제주 기준일이라 시스템 기본 시간대에 기대지 않는다. */
    public static final ZoneId JEJU_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 표기 흔들림을 지운다 - '성산일출봉'과 '성산 일출봉'을 같게 만든다.
     *
     * <p>⚠️ <b>괄호 안의 내용은 남긴다.</b> 지우면 매칭률이 77.5% → 80.4%로 오르지만,
     * 열안지오름(봉개동)과 열안지오름(오라동)처럼 <b>괄호가 유일한 구분자인 다른 장소가 합쳐진다</b>
     * (2026-08-24 실측 4건). 3%p 얻자고 틀린 혼잡도를 표시할 위험을 살 수는 없다.
     */
    public static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[\\s\\-_·,()\\[\\]]", "");
    }

    /**
     * 제주 현지 기준일 → UTC 시각. 명세서 16.0의 {@code forecast_at} 저장 규칙이다.
     *
     * <p>{@code 2026-08-23} → 제주 8/23 00:00 → <b>UTC 8/22 15:00</b>.
     *
     * <p>⚠️ 이 변환은 <b>저장할 때 한 번, 읽을 때 한 번</b>만 일어나야 한다. 중간에 한 번 더 끼면
     * 날짜가 이틀 밀리는데 값 자체는 그럴듯해서 캘린더가 어긋난 걸 눈치채기 어렵다.
     */
    public static LocalDateTime jejuDayToUtc(LocalDate jejuDay) {
        return jejuDay.atStartOfDay(JEJU_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /** {@link #jejuDayToUtc}의 역변환. 조회 응답이 화면에 날짜를 돌려줄 때 쓴다. */
    public static LocalDate utcToJejuDay(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(JEJU_ZONE)
                .toLocalDate();
    }

    private PlaceNameNormalizer() {
    }
}
