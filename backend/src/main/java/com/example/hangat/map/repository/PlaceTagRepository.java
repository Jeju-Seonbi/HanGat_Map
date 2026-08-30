package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceTag;
import com.example.hangat.map.model.entity.PlaceTagId;
import com.example.hangat.map.model.enums.TagSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 장소-태그 연결. 복합 PK라 키 타입이 {@link PlaceTagId}다. */
public interface PlaceTagRepository extends JpaRepository<PlaceTag, PlaceTagId> {

    /**
     * 실제로 장소가 하나라도 붙어 있는 태그만 - 화면 드롭다운을 채울 값.
     *
     * <p>246개를 전부 보여주면 제주에 없는 분류(스키장 등)까지 나온다. 붙은 개수를 같이 주면
     * 프론트가 "오름 (312)" 식으로 표시하거나 적은 것부터 잘라낼 수 있다.
     */
    @Query("""
            select t.code, t.name, count(pt)
            from PlaceTag pt
              join pt.tag t
            where t.isActive = true
            group by t.code, t.name
            order by count(pt) desc
            """)
    List<Object[]> countPlacesPerTag();

    /**
     * 재적재 때 이 장소의 API 태그만 걷어낸다.
     *
     * <p>{@code sourceType}으로 거르는 것이 핵심이다 - 운영자가 손으로 붙인 태그(ADMIN)까지
     * 지워 버리면 배치를 돌릴 때마다 사람 손이 지워진다.
     */
    void deleteByPlaceAndSourceType(Place place, TagSourceType sourceType);
}
