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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "course_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_day_position",
                columnNames = {"course_id", "day_no", "position"})
)
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor
public class CourseItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_no", nullable = false)
    private Integer dayNo;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_source", nullable = false)
    private CourseItemSource itemSource;

    @Column(name = "inbound_distance_m")
    private Integer inboundDistanceMeters;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "inbound_travel_minutes")
    private Integer inboundTravelMinutes;

    @Column(name = "planned_congestion_forecast_id")
    private Long plannedCongestionForecastId;

    @Column(name = "planned_weather_forecast_id")
    private Long plannedWeatherForecastId;

    @Column(name = "recommendation_score", precision = 8, scale = 4)
    private BigDecimal recommendationScore;

    @Column(name = "recommendation_reason_code", length = 30)
    private String recommendationReasonCode;

    @Column(name = "recommendation_reason", length = 300)
    private String recommendationReason;

    @Column(name = "replaced_from_place_id")
    private Long replacedFromPlaceId;

    @Column(name = "memo", length = 300)
    private String memo;

    public static CourseItem generated(
            Course course,
            Place place,
            int dayNo,
            int position,
            LocalDate visitDate,
            LocalTime startTime,
            CourseItemSource itemSource,
            String recommendationReason
    ) {
        CourseItem item = new CourseItem();
        item.course = course;
        item.place = place;
        item.dayNo = dayNo;
        item.position = position;
        item.visitDate = visitDate;
        item.startTime = startTime;
        item.itemSource = itemSource;
        item.recommendationReason = recommendationReason;
        return item;
    }
}
