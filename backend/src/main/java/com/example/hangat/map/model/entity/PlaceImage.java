package com.example.hangat.map.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 장소 사진(명세서 11.0) - KTO detailImage2, 장소당 여러 장.
 * 사진은 출처표시 의무가 있어 license_code·attribution 을 같이 저장한다.
 */
@Entity
@Table(
        name = "place_images",
        uniqueConstraints = {
                // 재적재 중복 방지. URL 원문(500자)은 UK를 못 걸어 해시로 건다
                @UniqueConstraint(name = "uk_place_images_url", columnNames = {"place_id", "url_hash"}),
                @UniqueConstraint(name = "uk_place_images_order", columnNames = {"place_id", "sort_order"})
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 장소 삭제 시 사진도 같이 삭제 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_mapping_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PlaceSourceMapping sourceMapping;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    /** KTO smallimageurl */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** image_url 의 SHA-256 - 중복 방지 UK 용 */
    @Column(name = "url_hash", length = 64, nullable = false)
    private String urlHash;

    /** KTO imgname */
    @Column(name = "caption", length = 200)
    private String caption;

    /** KTO cpyrhtDivCd - Type1(출처표시) / Type3(출처표시+변경금지) */
    @Column(name = "license_code", length = 20)
    private String licenseCode;

    /** 화면 표기 의무 문구 (공공누리) */
    @Column(name = "attribution", length = 100)
    private String attribution;

    /** 표시 순서 (KTO 응답 순서) */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 대표 사진 (메인 캐러셀용 1장) */
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    /** 명세서에 updated_at 없음 - BaseEntity 미상속 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PlaceImage() {
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
