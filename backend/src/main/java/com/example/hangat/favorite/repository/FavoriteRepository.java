package com.example.hangat.favorite.repository;

import com.example.hangat.favorite.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 찜 조회 저장소.
 * 모든 개인 데이터 조회 조건에 회원 ID를 포함한다.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** 카드에서 필요한 장소·카테고리·권역을 함께 조회한다. */
    @EntityGraph(attributePaths = {
            "place",
            "place.primaryCategory",
            "place.region"
    })
    Page<Favorite> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    Optional<Favorite> findByUserIdAndPlaceId(Long userId, Long placeId);
}
