package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 조회·저장.
 *
 * <p>조회 API용 메서드는 3단계에서 추가한다. 지금은 적재 배치가 쓰는 기본 CRUD만 필요하다.
 * "이미 있는 장소인가"는 {@link PlaceSourceMappingRepository}가 출처 ID로 판단하므로
 * 여기에 이름으로 찾는 메서드를 두지 않는다 - 같은 이름의 다른 장소가 실제로 존재한다.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {
}
