package com.example.hangat.map.model.entity;

import com.example.hangat.map.model.enums.TagType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 장소·여행 스타일 태그 - 테이블 명세서 7.0
 *
 * <p>화면의 "모든 종류의 관광지" 드롭다운을 채우는 값이다. {@code places}에는 대분류
 * ({@code primary_category_id})만 있고 세부 분류 컬럼이 없어서(§10-③), 세부 분류는 여기로 온다.
 *
 * <p>값의 출처는 KTO 분류체계({@code lclsSystmCode2}, 246개). 코드가 곧 {@code code},
 * 이름이 곧 {@code name}이다 - 예: {@code NA010100} = "산, 고개, 오름, 봉우리".
 * 우리가 임의로 만든 분류가 아니라 <b>공공기관 표준 분류</b>라 심사에서 근거를 댈 수 있다.
 *
 * <p>BaseEntity 미상속 사유는 {@link Region} 참고.
 */
@Entity
@Table(
        name = "tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tags_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_tags_name", columnNames = "name")
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Short id;

    /** KTO 분류체계 소분류 코드 (예: {@code NA010100}). */
    @Column(name = "code", length = 30, nullable = false)
    private String code;

    /** 화면 표시명 (예: "산, 고개, 오름, 봉우리"). */
    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /** columnDefinition 없이 varchar(20)으로 둔다 - 사유는 설계서 §9.1 ENUM 항목. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "tag_type", length = 20, nullable = false)
    private TagType tagType = TagType.PLACE;

    /** 대분류 이름 등 보조 설명. 예: "자연관광 &gt; 자연경관". */
    @Column(name = "description", length = 200)
    private String description;

    /** columnDefinition 필요 사유는 {@link Region#isActive()} 참고. */
    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 전용. */
    protected Tag() {
        this.tagType = TagType.PLACE;
        this.isActive = true;
    }

    /** 출처가 이름·설명을 고치면 따라간다. code는 이 태그의 정체성이라 바꾸지 않는다. */
    public void updateFromSource(String name, String description) {
        this.name = name;
        this.description = description;
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
