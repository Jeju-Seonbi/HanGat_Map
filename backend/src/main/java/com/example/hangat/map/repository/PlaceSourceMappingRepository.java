package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.PlaceSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 장소 ↔ 출처 매핑 조회.
 *
 * <p>적재 배치가 "이 장소를 전에 받은 적 있나"를 판단하는 통로다.
 * 이름이 아니라 <b>출처 쪽 ID</b>로 찾는다 - 같은 이름의 다른 장소가 실제로 있고,
 * KTO가 이름을 바꿔도 {@code contentid}는 그대로이기 때문이다.
 */
public interface PlaceSourceMappingRepository extends JpaRepository<PlaceSourceMapping, Long> {

    Optional<PlaceSourceMapping> findBySourceCodeAndSourcePlaceId(String sourceCode, String sourcePlaceId);

    /**
     * 한 출처의 매핑을 한 번에 읽는다. 2,147건을 하나씩 조회하면 쿼리가 2,147번 나가므로,
     * 배치 시작 시 이 메서드로 전부 읽어 메모리에서 대조한다.
     * {@code place}를 함께 로드해 저장 단계에서 지연 로딩이 터지지 않게 한다.
     */
    @Query("select m from PlaceSourceMapping m join fetch m.place where m.source.code = :sourceCode")
    List<PlaceSourceMapping> findAllBySourceCodeWithPlace(@Param("sourceCode") String sourceCode);
}
