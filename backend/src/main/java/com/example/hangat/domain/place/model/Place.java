package com.example.hangat.domain.place.model;

import com.example.hangat.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 장소 - TourAPI 관광지·음식점 + 행안부 착한가격업소 (시드·야간 배치로 적재) */
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TourAPI contentId - 착한가격업소 출신은 없다(null) */
    @Column(unique = true)
    private Long contentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceCategory category;

    @Column(length = 200)
    private String address;

    /** 하버사인 반경 검색용 - DECIMAL(10,7)이면 밀리미터 단위까지 충분 */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 500)
    private String imageUrl;

    /** 행안부 착한가격업소 여부 - 검증가 표시는 이 플래그가 있는 곳만 */
    @Column(nullable = false)
    private boolean goodPriceStore;

    /** 숨은 명소 플래그 - 점수식 가산·추천 근거에 사용 */
    @Column(nullable = false)
    private boolean hiddenGem;

    @Builder
    public Place(Long contentId, String name, Region region, PlaceCategory category,
                 String address, BigDecimal latitude, BigDecimal longitude,
                 String imageUrl, boolean goodPriceStore, boolean hiddenGem) {
        this.contentId = contentId;
        this.name = name;
        this.region = region;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.goodPriceStore = goodPriceStore;
        this.hiddenGem = hiddenGem;
    }
}
