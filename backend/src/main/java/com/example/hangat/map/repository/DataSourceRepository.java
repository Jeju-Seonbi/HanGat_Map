package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

/** 외부 출처 마스터 조회. PK가 코드 문자열이라 {@code findById("KTO")} 로 바로 찾는다. */
public interface DataSourceRepository extends JpaRepository<DataSource, String> {
}
