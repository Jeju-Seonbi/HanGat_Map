package com.example.hangat.domain.place;

import com.example.hangat.domain.place.model.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
