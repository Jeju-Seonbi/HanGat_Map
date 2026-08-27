package com.example.hangat.course;

import com.example.hangat.course.model.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
