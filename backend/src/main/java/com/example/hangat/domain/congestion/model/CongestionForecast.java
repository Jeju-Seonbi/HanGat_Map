package com.example.hangat.domain.congestion.model;

import com.example.hangat.common.model.BaseEntity;
import com.example.hangat.domain.place.model.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 혼잡 예보 - 관광공사 집중률(TatsCnctrRateService) 실측 기반, 장소 × 날짜 단위
 * ⚠️ 데이터 해상도는 날짜까지만 - 시간대 컬럼은 존재하지 않는다 (정직성 원칙)
 */
@Entity
@Table(name = "congestion_forecast",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_congestion_place_date",
                columnNames = {"place_id", "base_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CongestionForecast extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /** 예보 대상 날짜 (API의 baseYmd) */
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    /** 집중률 0~100 (API의 cnctrRate) */
    @Column(nullable = false)
    private double rate;

    @Builder
    public CongestionForecast(Place place, LocalDate baseDate, double rate) {
        this.place = place;
        this.baseDate = baseDate;
        this.rate = rate;
    }
}
