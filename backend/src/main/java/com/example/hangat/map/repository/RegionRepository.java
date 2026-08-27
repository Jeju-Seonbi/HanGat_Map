package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 권역 마스터 조회. 4행뿐이라 적재 배치는 시작할 때 한 번 읽어 메모리에 들고 쓴다. */
public interface RegionRepository extends JpaRepository<Region, Short> {

    Optional<Region> findByCode(String code);
}
