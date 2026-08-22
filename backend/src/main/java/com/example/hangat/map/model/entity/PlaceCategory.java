package com.example.hangat.map.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * 장소 카테고리(TOURIST/FOOD/CAFE/LODGING/CONVENIENCE 등) - 테이블 명세서 6.0
 *
 * <p>프론트의 type 필터가 이 code 값을 조건으로 쓴다(설계서 §2.1).
 * 값 추가는 테이블 행 추가로 끝나야 하므로(MART 등) 자바 enum이 아닌 문자열이다.
 * BaseEntity 미상속 사유는 {@link Region} 참고.
 */
@Entity
@Table(
        name = "place_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_place_categories_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_place_categories_name", columnNames = "name")
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceCategory {

    private static final short DEFAULT_DISPLAY_ORDER = 1;

    /** 명세서는 SMALLINT UNSIGNED. places.primary_category_id의 컬럼 타입을 이 필드가 결정한다({@link Region#getId()} 주석 참고). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /** regions.display_order는 TINYINT라 타입이 다르다. 이쪽은 UNIQUE가 아니라 INDEX라 명세서의 DEFAULT 1을 그대로 쓴다. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Short displayOrder = DEFAULT_DISPLAY_ORDER;

    /** columnDefinition 필요 사유는 {@link Region#isActive()} 주석 참고. */
    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 전용. */
    protected PlaceCategory() {
        this.displayOrder = DEFAULT_DISPLAY_ORDER;
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
