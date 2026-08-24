package com.example.hangat.map.model.entity;

import com.example.hangat.map.model.enums.CongestionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 장소 혼잡 예보 - 테이블 명세서 16.0
 *
 * <p>좌측 "한산한 곳 TOP 8" 순위와 지도 핀 색을 만드는 값이다(MAP_004).
 * 출처는 한국관광공사 관광지별 집중률(15128555).
 *
 * <p><b>BaseEntity를 상속하지 않는다.</b> 명세서에 {@code created_at}/{@code updated_at}이 없다 -
 * 이 테이블은 한 번 쓰고 고치지 않는 <b>이력</b>이라 수정 시각이라는 개념 자체가 없다.
 * "덮어쓰기하지 않고 발표 버전별 append"가 명세서 요구사항이다.
 *
 * <p><b>데이터가 없으면 행을 만들지 않는다.</b> rate 0이나 QUIET로 채우지 않는다 -
 * 명세서에 명시된 규칙이고, 그래야 화면이 '정보 없음'(회색 핀)으로 구분해 표시할 수 있다.
 */
@Entity
@Table(
        name = "congestion_forecasts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_congestion_place_forecast_base",
                columnNames = {"place_id", "forecast_at", "base_at"}),
        indexes = {
                @Index(name = "idx_congestion_place_forecast", columnList = "place_id, forecast_at, base_at"),
                @Index(name = "idx_congestion_base_at", columnList = "base_at"),
                @Index(name = "idx_congestion_lookup", columnList = "forecast_at, base_at, level, rate")
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CongestionForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_congestion_place"))
    private Place place;

    /**
     * 예보 대상 시각. <b>UTC로 저장한다</b>(명세서: "UTC 저장. DAILY는 제주 현지 기준일 시작 시각을 UTC로 변환").
     *
     * <p>⚠️ 집중률은 일 단위라 {@code baseYmd=20260823}이 곧 <b>KST 8/23 00:00</b>이고,
     * 저장값은 <b>UTC 8/22 15:00</b>이 된다. 조회할 때 되돌리지 않으면 화면 날짜가 하루 밀린다.
     * 변환은 {@code CongestionIngestService}가 한 곳에서만 하고 여기서는 받은 값을 그대로 쓴다.
     */
    @Column(name = "forecast_at", nullable = false)
    private LocalDateTime forecastAt;

    /**
     * 예보 발표 시각 = 발표 버전 식별자.
     *
     * <p><b>API가 이 값을 주지 않는다</b>(응답 필드는 baseYmd/areaCd/signguCd/tAtsNm/cnctrRate뿐).
     * 그래서 <b>배치 실행일 00:00</b>으로 우리가 채운다 - 하루 한 버전이 쌓이고,
     * 같은 날 두 번 돌려도 UNIQUE가 버전을 늘리지 않는다.
     */
    @Column(name = "base_at", nullable = false)
    private LocalDateTime baseAt;

    /** rate와 함께 수집 시점에 확정한다 - 조회할 때마다 계산하지 않는다(명세서 상세설명). */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 20, nullable = false)
    private CongestionLevel level;

    /** 그 장소 최성수기 대비 %(0~100). 장소 간 비교 금지 - {@link CongestionLevel} 참고. */
    @Column(name = "rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_congestion_source"))
    private DataSource source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /** JPA 전용. */
    protected CongestionForecast() {
    }

    /** rate에서 level을 파생해 한 번에 만든다 - 둘이 어긋난 행이 생길 자리를 없앤다. */
    public static CongestionForecast of(Place place, DataSource source,
                                        LocalDateTime forecastAt, LocalDateTime baseAt,
                                        BigDecimal rate) {
        return CongestionForecast.builder()
                .place(place)
                .source(source)
                .forecastAt(forecastAt)
                .baseAt(baseAt)
                .rate(rate)
                .level(CongestionLevel.from(rate))
                .build();
    }

    @PrePersist
    void onCreate() {
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
    }
}
