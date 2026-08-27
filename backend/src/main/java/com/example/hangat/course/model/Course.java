package com.example.hangat.course.model;

import com.example.hangat.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @ManyToOne
//    @JoinColumn(name = "user_id")
//    private User user;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "preset_id")
    private Long presetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_reason", nullable = false)
    private GenerationReason generationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseStatus status;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "people", nullable = false)
    private Integer people;

    @Column(name = "budget_total")
    private Integer budgetTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport", nullable = false)
    private Transport transport;

    @Column(name = "input_fingerprint", length = 64)
    private String inputFingerprint;

    @Column(name = "algorithm_version", length = 30)
    private String algorithmVersion;

    @Column(name = "estimated_cost_min")
    private Integer estimatedCostMin;
    //백엔드가 계산한 예상 최소 비용

    @Column(name = "estimated_cost_max")
    private Integer estimatedCostMax;

    @Column(name = "average_congestion_rate", precision = 5, scale = 2)
    private BigDecimal averageCongestionRate;

    @Column(name = "generation_error_code", length = 50)
    private String generationErrorCode;

    @Column(name = "generation_completed_at")
    private LocalDateTime generationCompletedAt;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Course ready(
            LocalDate startDate,
            LocalDate endDate,
            Integer people,
            Integer budgetTotal,
            Transport transport,
            GenerationReason generationReason,
            String algorithmVersion
    ) {
        Course course = new Course();
        course.courseType = CourseType.USER;
        course.generationReason = generationReason == null
                ? GenerationReason.INITIAL
                : generationReason;
        course.status = CourseStatus.READY;
        course.startDate = startDate;
        course.endDate = endDate;
        course.people = people;
        course.budgetTotal = budgetTotal;
        course.transport = transport;
        course.algorithmVersion = algorithmVersion;
        course.generationCompletedAt = LocalDateTime.now();
        return course;
    }
}
