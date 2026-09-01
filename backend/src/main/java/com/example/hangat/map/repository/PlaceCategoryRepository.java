package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 장소 카테고리 마스터 조회. 7행뿐이라 적재 배치가 시작할 때 한 번 읽는다. */
public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Short> {

    Optional<PlaceCategory> findByCode(String code);
}
