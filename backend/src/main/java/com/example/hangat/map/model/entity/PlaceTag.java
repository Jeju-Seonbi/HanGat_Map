package com.example.hangat.map.model.entity;

import com.example.hangat.map.model.enums.TagSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 장소 - 태그 연결 - 테이블 명세서 13.0
 *
 * <p>복합 PK {@code (place_id, tag_id)}라 중복 삽입이 DB에서 막힌다({@link PlaceTagId}).
 *
 * <p>{@code weight}는 "이 장소가 이 태그에 얼마나 해당하는가"다. KTO 분류처럼 출처가 단정한 값은
 * 1.0000, 후기·모델에서 추론한 값은 그보다 낮게 들어온다.
 */
@Entity
@Table(name = "place_tags")
@IdClass(PlaceTagId.class)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceTag {

    /** FK 컬럼 타입은 대상 엔티티의 {@code @Id} 타입을 따른다 - {@link Place#getId()} = BIGINT. */
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_place_tags_place"))
    private Place place;

    /** {@link Tag#getId()} = SMALLINT. */
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_place_tags_tag"))
    private Tag tag;

    /** 관련도 0.0000 ~ 1.0000. API 분류는 항상 1.0000. */
    @Column(name = "weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private TagSourceType sourceType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 전용. */
    protected PlaceTag() {
    }

    /** KTO 분류처럼 출처가 단정하는 태그. */
    public static PlaceTag fromApi(Place place, Tag tag) {
        return PlaceTag.builder()
                .place(place)
                .tag(tag)
                .weight(BigDecimal.ONE)
                .sourceType(TagSourceType.API)
                .build();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
