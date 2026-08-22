package com.example.hangat.map.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.example.hangat.map.model.enums.BusinessStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 장소 통합(관광지·음식점·카페·숙소·편의점·마트) - 테이블 명세서 9.0
 *
 * <p>명세서에 없는 컬럼은 만들지 않는다. is_active는 places에 존재하지 않고(설계서 §10-②),
 * 프론트가 쓰는 fee·체류시간·실내여부도 명세서에 없다(§10-③) - 임의로 추가하지 말 것.
 *
 * <p>명세서 대비 의도적 편차: UNSIGNED는 H2가 파싱하지 못해 재현하지 않는다(전 컬럼 SIGNED).
 * schema.sql을 도입하더라도 거기에도 넣지 말 것 - 드라이버가 unsigned를 상위 JDBC 타입으로 보고하면
 * 운영(validate)에서 타입 불일치로 부팅이 실패한다. CHECK·성능 인덱스도 미생성(§10-④).
 *
 * <p>BaseEntity 미상속 사유는 {@link Region} 참고.
 */
@Entity
@Table(name = "places")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Place {

    private static final BigDecimal DEFAULT_RATING_AVG = new BigDecimal("0.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 값 타입이 아니라 연관관계인 이유: 팀 규칙상 네이티브 쿼리를 못 쓰므로(테스트가 H2)
     * JPQL join·fetch join이 가능해야 한다. 목록 조회 N+1은 LAZY + default_batch_fetch_size로 막는다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    /** ?type= 필터가 primaryCategory.code를 조건으로 쓴다(설계서 §2.1). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_category_id", nullable = false)
    private PlaceCategory primaryCategory;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** KTO 집중률 API가 좌표 없이 관광지명만 주기 때문에 이 컬럼이 이름 매칭 키가 된다(설계서 §3). */
    @Column(name = "normalized_name", length = 200, nullable = false)
    private String normalizedName;

    @Column(name = "road_address", length = 300)
    private String roadAddress;

    @Column(name = "lot_address", length = 300)
    private String lotAddress;

    /** DECIMAL(10,7). double을 쓰면 좌표에 반올림 오차가 남고 DDL도 명세서와 달라진다. */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * @Lob은 MariaDB에서 LONGTEXT가 되어 명세서와 달라지므로 columnDefinition으로 명시한다.
     * H2에서는 CLOB이 되고 H2는 CLOB에 SELECT DISTINCT를 거부하니, 이 컬럼을 포함한 조회에 distinct를 쓰지 말 것
     * (MariaDB에서는 돌고 테스트에서만 깨진다). 목록 API는 projection으로 이 컬럼을 제외한다.
     */
    @Column(name = "overview", columnDefinition = "TEXT")
    private String overview;

    @Column(name = "phone", length = 30)
    private String phone;

    /** 프론트 hours. null이면 상시 개방이라 상세 화면에서 운영시간 줄 자체가 나오지 않는다(설계서 §1.2). */
    @Column(name = "operating_hours_text", length = 500)
    private String operatingHoursText;

    @Column(name = "rest_day_text", length = 300)
    private String restDayText;

    /** 명세서가 NULL 허용이므로 primitive가 아닌 Boolean. columnDefinition 필요 사유는 {@link Region#isActive()} 참고. */
    @Column(name = "parking_available", columnDefinition = "BOOLEAN")
    private Boolean parkingAvailable;

    @Column(name = "toilet_available", columnDefinition = "BOOLEAN")
    private Boolean toiletAvailable;

    /**
     * 명세서 DEFAULT 'UNKNOWN'을 자바 쪽에서 재현한다. NOT NULL 참조형이라 빌더에서 한 줄 빠지면
     * 빌드는 통과하고 저장 시점에 터진다. 미수집을 OPEN으로 낙관 추정하지 않는다는 원칙과도 맞는다.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "business_status", length = 20, nullable = false)
    private BusinessStatus businessStatus = BusinessStatus.UNKNOWN;

    /** 착한가격업소 여부. 프론트 type=food가 이 플래그로 매핑된다(설계서 §2.1). */
    @Column(name = "is_good_price", nullable = false, columnDefinition = "BOOLEAN")
    private boolean isGoodPrice;

    @Column(name = "good_price_base_date")
    private LocalDate goodPriceBaseDate;

    @Column(name = "is_hidden_gem", nullable = false, columnDefinition = "BOOLEAN")
    private boolean isHiddenGem;

    @Column(name = "hidden_gem_score", precision = 6, scale = 3)
    private BigDecimal hiddenGemScore;

    @Column(name = "hidden_gem_algorithm_version", length = 30)
    private String hiddenGemAlgorithmVersion;

    @Column(name = "hidden_gem_calculated_at")
    private LocalDateTime hiddenGemCalculatedAt;

    /**
     * 후기 평점 요약(비정규화). 후기 작성·삭제 시 갱신하며 별점 0인 후기는 평균에서 제외한다(설계서 §2.4).
     * BigDecimal.ZERO는 scale이 0이라 DB 왕복 값(0.00)과 equals가 false다 - 비교는 compareTo,
     * 갱신 결과는 setScale(2, HALF_UP)로 맞춘다.
     */
    @Builder.Default
    @Column(name = "rating_avg", precision = 3, scale = 2, nullable = false)
    private BigDecimal ratingAvg = DEFAULT_RATING_AVG;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 전용. */
    protected Place() {
        this.businessStatus = BusinessStatus.UNKNOWN;
        this.ratingAvg = DEFAULT_RATING_AVG;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
