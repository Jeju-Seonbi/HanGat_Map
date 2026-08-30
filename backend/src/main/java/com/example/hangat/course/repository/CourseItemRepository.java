package com.example.hangat.course.repository;

import com.example.hangat.course.model.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 코스 일정 조회 - 코스 상세·스왑 대상 찾기가 쓴다. 쓰기는 생성 엔진·스왑 서비스 몫. */
public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

    /**
     * 코스 상세 한 판 - 화면 순서(일차 → 순서)대로.
     *
     * <p>place를 fetch join하는 이유: 일정마다 장소 이름·좌표·이미지를 그리므로 LAZY로 두면
     * 항목 수만큼 추가 쿼리가 난다. region까지 당기는 건 카드에 권역 라벨이 붙기 때문
     * ({@code MainService}의 한산 장소 카드와 같은 구성).
     */
    @Query("""
            select i from CourseItem i
            join fetch i.place p
            join fetch p.region
            where i.course.id = :courseId
            order by i.dayNo, i.position
            """)
    List<CourseItem> findItemsWithPlace(@Param("courseId") Long courseId);
}
