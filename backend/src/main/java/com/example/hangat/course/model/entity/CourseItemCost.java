package com.example.hangat.course.model.entity;

import com.example.hangat.course.model.enums.CostAccuracy;
import com.example.hangat.course.model.enums.CostCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 코스 비용 산정 항목 - 테이블 명세서 26.0
 *
 * <p>예상 경비의 <b>원장</b>이다. courses.estimated_cost_min/max는 이 테이블의 합산 캐시일 뿐이다.
 * "예산·취향 분산 코스"의 예산 하드 제약과 가격 정직성 라벨(검증가/추정/요금 확인 필요)이 여기서 나온다.
 *
 * <p>정확도별 금액 불변식(명세서 CHECK - 앱에서 지킨다)은 빌더가 아니라
 * {@link #verified}/{@link #estimated}/{@link #unknown} 팩토리로만 만들게 해서 강제한다.
 *
 * <p>BaseEntity 미상속: 명세서에 created_at/updated_at이 없고 calculated_at뿐이다 -
 * 재계산 시 행을 고치지 않고 지우고 다시 만든다.
 */
@Entity
@Table(
        name = "course_item_costs",
        indexes = @Index(name = "idx_course_item_costs_course_category", columnList = "course_id, category")
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseItemCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_course_item_costs_course"))
    @OnDelete(action = OnDeleteAction.CASCADE)   // 명세서: ON DELETE CASCADE
    private Course course;

    /** 교통비 등 특정 일정에 붙지 않는 코스 공통 비용은 NULL(명세서). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_item_id",
            foreignKey = @ForeignKey(name = "fk_course_item_costs_item"))
    @OnDelete(action = OnDeleteAction.CASCADE)   // 명세서: ON DELETE CASCADE
    private CourseItem courseItem;

    /**
     * 검증 식비의 근거 메뉴(place_menus.id). <b>연관관계가 아니라 Long인 이유</b>:
     * place_menus(14.0) 엔티티가 아직 미구현이다(착한가격 CSV 적재 대기). 생기면 FK 전환.
     */
    @Column(name = "menu_id")
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private CostCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "accuracy_type", length = 20, nullable = false)
    private CostAccuracy accuracyType;

    /** 코스 인원을 반영한 총액(원). 단가가 아니다 - basis_text에 "8,000원 × 2명"처럼 셈을 남긴다. */
    @Column(name = "amount_min")
    private Integer amountMin;

    @Column(name = "amount_max")
    private Integer amountMax;

    @Builder.Default
    @Column(name = "currency", nullable = false, columnDefinition = "CHAR(3)")
    private String currency = "KRW";

    @Column(name = "basis_text", length = 300)
    private String basisText;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** JPA 전용. */
    protected CourseItemCost() {
        this.currency = "KRW";
    }

    /**
     * 검증가 - <b>착한가격업소 메뉴 등 실측 근거가 있을 때만.</b> min=max 한 값으로 고정된다.
     * menuId 없이 검증가를 만들고 싶어지면 그 값은 사실 추정이다 - {@link #estimated}를 쓸 것.
     * "검증가" 라벨이 근거 없이 붙는 게 정직성 원칙의 유일한 구멍이라 여기서 막는다.
     */
    public static CourseItemCost verified(Course course, CourseItem courseItem, Long menuId,
                                          CostCategory category, int amount, String basisText) {
        if (menuId == null) {
            throw new IllegalArgumentException("근거 메뉴 없는 검증가는 없다 - estimated()를 쓸 것");
        }
        return CourseItemCost.builder()
                .course(course).courseItem(courseItem).menuId(menuId)
                .category(category).accuracyType(CostAccuracy.VERIFIED)
                .amountMin(amount).amountMax(amount)
                .basisText(basisText)
                .build();
    }

    /** 추정 범위. min ≤ max 불변식(명세서 CHECK)도 다른 팩토리처럼 여기서 강제한다. */
    public static CourseItemCost estimated(Course course, CourseItem courseItem,
                                           CostCategory category, Integer amountMin, Integer amountMax,
                                           String basisText) {
        if (amountMin != null && amountMax != null && amountMin > amountMax) {
            throw new IllegalArgumentException(
                    "추정 범위가 뒤집혔다(min " + amountMin + " > max " + amountMax + ") - 계산 코드 버그");
        }
        return CourseItemCost.builder()
                .course(course).courseItem(courseItem)
                .category(category).accuracyType(CostAccuracy.ESTIMATED)
                .amountMin(amountMin).amountMax(amountMax)
                .basisText(basisText)
                .build();
    }

    /** 산정 불가 - 금액 없이 "요금 확인 필요"로만. 없는 가격을 지어내지 않는다(명세서 CHECK). */
    public static CourseItemCost unknown(Course course, CourseItem courseItem, CostCategory category) {
        return CourseItemCost.builder()
                .course(course).courseItem(courseItem)
                .category(category).accuracyType(CostAccuracy.UNKNOWN)
                .build();
    }

    @PrePersist
    void onCreate() {
        if (this.calculatedAt == null) {
            this.calculatedAt = LocalDateTime.now();
        }
    }
}
