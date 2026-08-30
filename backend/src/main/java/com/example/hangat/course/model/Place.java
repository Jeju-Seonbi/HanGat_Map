package com.example.hangat.course.model;

import com.example.hangat.common.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "places")
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_category_id", nullable = false)
    private PlaceCategory primaryCategory;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "road_address", length = 300)
    private String roadAddress;

    @Column(name = "lot_address", length = 300)
    private String lotAddress;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "overview", columnDefinition = "TEXT")
    private String overview;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "operating_hours_text", length = 500)
    private String operatingHoursText;

    @Column(name = "rest_day_text", length = 300)
    private String restDayText;

    @Column(name = "parking_available")
    private Boolean parkingAvailable;

    @Column(name = "toilet_available")
    private Boolean toiletAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_status", nullable = false)
    private BusinessStatus businessStatus;

    @Column(name = "is_good_price", nullable = false)
    private boolean goodPrice;

    @Column(name = "is_hidden_gem", nullable = false)
    private boolean hiddenGem;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAverage;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    public static Place fromExternalCandidate(
            Region region,
            PlaceCategory primaryCategory,
            String name,
            String normalizedName,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        Place place = new Place();
        place.region = region;
        place.primaryCategory = primaryCategory;
        place.name = name;
        place.normalizedName = normalizedName;
        place.roadAddress = roadAddress;
        place.lotAddress = lotAddress;
        place.latitude = latitude;
        place.longitude = longitude;
        place.businessStatus = BusinessStatus.UNKNOWN;
        place.goodPrice = false;
        place.hiddenGem = false;
        place.ratingAverage = BigDecimal.ZERO;
        place.reviewCount = 0;
        return place;
    }
}
