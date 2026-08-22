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

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_reason", nullable = false)
    private GenerationReason generationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseStatus status;

    @Column(name = "title")
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "people", nullable = false)
    private Integer people;

    @Column(name = "budget_total", nullable = false)
    private Integer budgetTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport", nullable = false)
    private Transport transport;

    @Column(name = "input_fingerprint", length = 64)
    private String inputFingerprint;

    @Column(name = "algorithm_version")
    private String algorithmVersion;

    @Column(name = "estimated_cost_min")
    private Integer estimatedCostMin;
    //백엔드가 계산한 예상 최소 비용

    @Column(name = "estimated_cost_max")
    private Integer estimatedCostMax;

    @Column(name = "average_congestion_rate")
    private BigDecimal averageCongestionRate;

    @Column(name = "generation_error_code")
    private String generationErrorCode;

    @Column(name = "generation_completed_at")
    private LocalDateTime generationCompletedAt;

//    @Column(name = "saved_at")
//    private LocalDateTime savedAt;
    // save_at와 delete_at는 굳이 있어야 하나 라는 의문 추후 마이페이지에서
    // 순서라면 그냥 user_id와 courses_id로 순서대로 나열이 가능함

//    @Column(name = "deleted_at")
//    private LocalDateTime deletedAt;


}
