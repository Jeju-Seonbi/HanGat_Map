package com.example.hangat.course;

import com.example.hangat.course.model.PlaceSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceSourceMappingRepository extends JpaRepository<PlaceSourceMapping, Long> {
    Optional<PlaceSourceMapping> findBySourceCodeAndSourcePlaceId(
            String sourceCode,
            String sourcePlaceId
    );
}
