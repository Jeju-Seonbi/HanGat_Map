package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByPlaceIdAndStatusOrderByCreatedAtDesc(Long placeId, ReviewStatus status, Pageable pageable);
}
