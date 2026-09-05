package com.example.hangat.map.service;

import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.DataSourceRepository;
import com.example.hangat.map.repository.RegionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스터 데이터 초기화 - 날씨 적재(V2)가 기대는 두 가지를 못 박는다.
 * 권역의 기상청 격자와 기상청 출처(KMA_SHORT / KMA_MID) 행.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MapMasterDataInitializerTest {

    @Autowired MapMasterDataInitializer initializer;
    @Autowired RegionRepository regionRepository;
    @Autowired DataSourceRepository dataSourceRepository;
    @Autowired EntityManager em;

    /**
     * 스위트 관례: 마스터가 비워진 컨텍스트(다른 테스트가 regions·data_sources를 deleteAll한 뒤)를 이어받아도
     * 자립한다. 초기화는 멱등이라 부팅 때 이미 돌았어도 다시 불러 해가 없다.
     */
    @BeforeEach
    void initialize() {
        initializer.run(null);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("빈 DB 기동 시 권역 4행에 기상청 격자가 함께 들어간다 - 북부는 기존 단일 격자 52/38과 같다")
    void seedsRegionsWithKmaGrid() {
        Map<String, Region> byCode = regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getCode, region -> region));

        assertThat(byCode).containsKeys("NORTH", "EAST", "SOUTH", "WEST");
        assertThat(byCode.get("NORTH").getKmaGridX()).isEqualTo((short) 52);
        assertThat(byCode.get("NORTH").getKmaGridY()).isEqualTo((short) 38);
        byCode.values().forEach(region -> {
            assertThat(region.getKmaGridX()).as("%s gridX", region.getCode()).isNotNull();
            assertThat(region.getKmaGridY()).as("%s gridY", region.getCode()).isNotNull();
        });
    }

    @Test
    @DisplayName("권역이 먼저 들어간 DB(운영)는 행을 그대로 두고 비어 있는 격자만 채운다")
    void backfillsMissingGridWithoutTouchingRows() {
        em.createQuery("update Region r set r.kmaGridX = null, r.kmaGridY = null").executeUpdate();
        em.clear();
        long before = regionRepository.count();

        initializer.run(null);
        em.flush();
        em.clear();

        assertThat(regionRepository.count()).isEqualTo(before);
        regionRepository.findAll().forEach(region -> {
            assertThat(region.getKmaGridX()).as("%s gridX", region.getCode()).isNotNull();
            assertThat(region.getKmaGridY()).as("%s gridY", region.getCode()).isNotNull();
        });
    }

    @Test
    @DisplayName("기상청 단기·중기 출처 행이 있어 weather_forecasts.source_code FK를 걸 수 있다")
    void seedsKmaDataSources() {
        assertThat(dataSourceRepository.existsById("KMA_SHORT")).isTrue();
        assertThat(dataSourceRepository.existsById("KMA_MID")).isTrue();
        assertThat(dataSourceRepository.findById("KMA_MID").orElseThrow().getDisclaimerText())
                .contains("제주도 전역");
    }
}
