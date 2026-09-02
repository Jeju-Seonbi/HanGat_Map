package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 태그 마스터 조회. 246행뿐이라 적재 배치는 시작할 때 통째로 읽어 코드→엔티티 맵으로 들고 쓴다. */
public interface TagRepository extends JpaRepository<Tag, Short> {

    Optional<Tag> findByCode(String code);

    List<Tag> findAllByIsActiveTrue();
}
