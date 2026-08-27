package com.example.hangat.map.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 외부 데이터 출처 - 테이블 명세서 8.0
 *
 * <p>{@code attribution_text}가 화면 푸터의 출처 표기에 쓰인다. 공공데이터 이용 조건이자
 * 심사에서 확인하는 항목이므로 출처를 추가할 때 반드시 채운다.
 *
 * <p>PK가 {@code AUTO_INCREMENT}가 아니라 <b>코드 문자열</b>이다(명세서 그대로).
 * {@code place_source_mappings.source_code}가 이 값을 FK로 가리키므로,
 * 적재 코드에서 {@code "KTO"} 같은 리터럴을 그대로 쓸 수 있어 조인 없이 읽힌다.
 *
 * <p>BaseEntity 미상속 사유는 {@link Region} 참고.
 */
@Entity
@Table(
        name = "data_sources",
        uniqueConstraints = @UniqueConstraint(name = "uk_data_sources_display_name", columnNames = "display_name")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DataSource {

    /** KTO / KTO_CNCTR / SBIZ / MOIS_GOODPRICE 등. 참조 컬럼과 문자셋이 같아야 FK가 걸린다. */
    @Id
    @Column(name = "code", length = 30, nullable = false)
    private String code;

    /** 푸터·상세 화면에 노출할 출처 표기명. */
    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    /** 한국관광공사 / 소상공인시장진흥공단 / 기상청 등. */
    @Column(name = "provider_name", length = 100, nullable = false)
    private String providerName;

    @Column(name = "homepage_url", length = 1000)
    private String homepageUrl;

    /** 공식 문서 또는 공공데이터포털 상세 주소. */
    @Column(name = "api_url", length = 1000)
    private String apiUrl;

    /** 공공누리 제1유형 등 확인된 명칭. */
    @Column(name = "license_name", length = 100)
    private String licenseName;

    @Column(name = "license_url", length = 1000)
    private String licenseUrl;

    /** ★ 화면·보고서에 노출할 표준 출처 문구. 푸터 출처 표기 요건과 직결된다. */
    @Column(name = "attribution_text", length = 300, nullable = false)
    private String attributionText;

    /** 예측값·외부 데이터 오차 가능성 등. 집중률처럼 '예측'인 출처에 특히 필요하다. */
    @Column(name = "disclaimer_text", length = 500)
    private String disclaimerText;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    /** columnDefinition 필요 사유는 {@link Region#isActive()} 참고. */
    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 전용. */
    protected DataSource() {
        this.isActive = true;
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
