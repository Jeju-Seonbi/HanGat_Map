package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    List<PlaceImage> findByPlaceIdOrderBySortOrder(Long placeId);

    /** 재적재용 선삭제 - 일반 delete 는 INSERT 가 먼저 나가 UK 충돌이 난다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlaceImage i where i.place = :place")
    void deleteByPlace(@Param("place") Place place);
}
