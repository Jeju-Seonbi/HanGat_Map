package com.example.hangat.map.service;

import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.PlaceSourceMapping;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.DataSourceRepository;
import com.example.hangat.map.repository.PlaceCategoryRepository;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.repository.PlaceSourceMappingRepository;
import com.example.hangat.map.repository.RegionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 적재 청크 하나를 저장하는 전담 컴포넌트.
 *
 * <p><b>왜 별도 클래스인가</b>: 청크마다 트랜잭션을 끊기 위해서다.
 * 2,147건을 한 트랜잭션으로 묶으면 마지막 1건이 실패할 때 전부 롤백된다.
 * 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면 프록시를 안 거쳐 적용되지 않으므로
 * 호출하는 쪽({@link PlaceIngestService})과 분리했다.
 *
 * <p>중간에 실패해도 앞 청크는 남고, 다시 돌리면 이어서 채운다 -
 * 중복은 {@code UNIQUE(source_code, source_place_id)}가 막는다.
 */
@Component
public class PlaceIngestWriter {

    private final PlaceRepository placeRepository;
    private final PlaceSourceMappingRepository mappingRepository;
    private final RegionRepository regionRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final DataSourceRepository dataSourceRepository;

    public PlaceIngestWriter(PlaceRepository placeRepository,
                             PlaceSourceMappingRepository mappingRepository,
                             RegionRepository regionRepository,
                             PlaceCategoryRepository categoryRepository,
                             DataSourceRepository dataSourceRepository) {
        this.placeRepository = placeRepository;
        this.mappingRepository = mappingRepository;
        this.regionRepository = regionRepository;
        this.categoryRepository = categoryRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    /** 저장 대상 한 건. 파싱·판정이 끝난 상태로 넘어온다. */
    public record Row(String sourcePlaceId, String regionCode, String categoryCode,
                      String name, String normalizedName, String roadAddress,
                      BigDecimal latitude, BigDecimal longitude, String phone,
                      String dataHash, String rawPayload, LocalDateTime sourceUpdatedAt) {
    }

    /** 청크 처리 결과. */
    public record ChunkResult(int inserted, int updated, int unchanged) {
    }

    @Transactional
    public ChunkResult saveChunk(String sourceCode, Iterable<Row> rows) {
        DataSource source = dataSourceRepository.findById(sourceCode)
                .orElseThrow(() -> new IllegalStateException(
                        "data_sources에 '" + sourceCode + "' 행이 없다. data.sql 적재를 먼저 확인할 것"));

        // 마스터는 4행·7행뿐이라 트랜잭션 안에서 한 번만 읽어 재사용한다
        Map<String, Region> regions = new HashMap<>();
        Map<String, PlaceCategory> categories = new HashMap<>();

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;

        for (Row row : rows) {
            Region region = regions.computeIfAbsent(row.regionCode(),
                    code -> regionRepository.findByCode(code)
                            .orElseThrow(() -> new IllegalStateException("regions에 '" + code + "' 없음")));
            PlaceCategory category = categories.computeIfAbsent(row.categoryCode(),
                    code -> categoryRepository.findByCode(code)
                            .orElseThrow(() -> new IllegalStateException("place_categories에 '" + code + "' 없음")));

            Optional<PlaceSourceMapping> found =
                    mappingRepository.findBySourceCodeAndSourcePlaceId(sourceCode, row.sourcePlaceId());

            if (found.isEmpty()) {
                Place place = placeRepository.save(Place.builder()
                        .region(region)
                        .primaryCategory(category)
                        .name(row.name())
                        .normalizedName(row.normalizedName())
                        .roadAddress(row.roadAddress())
                        .latitude(row.latitude())
                        .longitude(row.longitude())
                        .phone(row.phone())
                        .isGoodPrice(false)
                        .isHiddenGem(false)
                        .reviewCount(0)
                        .build());

                mappingRepository.save(PlaceSourceMapping.builder()
                        .place(place)
                        .source(source)
                        .sourcePlaceId(row.sourcePlaceId())
                        .dataHash(row.dataHash())
                        .rawPayload(row.rawPayload())
                        .sourceUpdatedAt(row.sourceUpdatedAt())
                        .lastSyncedAt(LocalDateTime.now())
                        .build());
                inserted++;
                continue;
            }

            PlaceSourceMapping mapping = found.get();
            // 원본이 그대로면 UPDATE를 건너뛴다 - 2,147건을 매번 쓰지 않는다
            if (row.dataHash() != null && row.dataHash().equals(mapping.getDataHash())) {
                unchanged++;
                continue;
            }

            mapping.getPlace().updateFromSource(region, category, row.name(), row.normalizedName(),
                    row.roadAddress(), row.latitude(), row.longitude(), row.phone());
            mapping.markSynced(row.dataHash(), row.rawPayload(), row.sourceUpdatedAt());
            updated++;
        }

        return new ChunkResult(inserted, updated, unchanged);
    }
}
