package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Review;
import com.example.hangat.map.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByPlaceIdAndStatusOrderByCreatedAtDesc(Long placeId, ReviewStatus status, Pageable pageable);

    /** [별점 평균, 전체 건수]. AVG 는 별점 null(제보만 후기)을 알아서 뺀다 */
    @Query("select avg(r.rating), count(r) from Review r where r.place.id = :placeId and r.status = 'ACTIVE'")
    Object[] summarize(@Param("placeId") Long placeId);
}
