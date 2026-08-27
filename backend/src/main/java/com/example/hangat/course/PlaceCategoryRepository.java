package com.example.hangat.course;

import com.example.hangat.course.model.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {
    Optional<PlaceCategory> findByCodeIgnoreCaseAndActiveTrue(String code);
}
