package com.example.hangat.course;

import com.example.hangat.course.model.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {
    Optional<Region> findByCodeIgnoreCaseAndActiveTrue(String code);
}
