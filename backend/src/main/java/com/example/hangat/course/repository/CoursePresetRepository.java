package com.example.hangat.course.repository;

import com.example.hangat.course.model.entity.CoursePreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CoursePresetRepository extends JpaRepository<CoursePreset, Long> {

    /** 배치·운영 스크립트용 안정 식별자 조회. */
    Optional<CoursePreset> findByCode(String code);

    /**
     * 메인 카드·샘플 배치 대상. 파생 쿼리가 아닌 JPQL인 이유: boolean 필드명이 {@code isActive}라
     * 프로퍼티명 해석(is 접두 제거)이 갈릴 수 있다 - 경로를 직접 쓰면 모호함이 없다.
     */
    @Query("select p from CoursePreset p where p.isActive = true order by p.id")
    List<CoursePreset> findActivePresets();
}
