package com.example.hangat.map.store;

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
import com.example.hangat.map.service.PlaceNameNormalizer;
import com.example.hangat.map.service.RegionResolver;
import com.example.hangat.map.store.SbizStoreClient.StoreItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 상가 한 곳 저장. bizesId(공단 고유 ID) 기준 멱등 - 재실행해도 중복이 안 생긴다. */
@Component
public class StoreIngestWriter {

    static final String SOURCE = "SBIZ";

    private final PlaceRepository placeRepository;
    private final PlaceSourceMappingRepository mappingRepository;
    private final DataSourceRepository dataSourceRepository;
    private final RegionRepository regionRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final RegionResolver regionResolver;

    public StoreIngestWriter(PlaceRepository placeRepository,
                             PlaceSourceMappingRepository mappingRepository,
                             DataSourceRepository dataSourceRepository,
                             RegionRepository regionRepository,
                             PlaceCategoryRepository categoryRepository,
                             RegionResolver regionResolver) {
        this.placeRepository = placeRepository;
        this.mappingRepository = mappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.regionRepository = regionRepository;
        this.categoryRepository = categoryRepository;
        this.regionResolver = regionResolver;
    }

    public enum Outcome { INSERTED, ALREADY, NO_REGION }

    @Transactional
    public Outcome upsert(StoreItem item, String categoryCode) {
        if (mappingRepository.findBySourceCodeAndSourcePlaceId(SOURCE, item.bizesId()).isPresent()) {
            return Outcome.ALREADY;
        }
        String address = item.roadAddress() != null ? item.roadAddress() : item.lotAddress();
        String regionCode = regionResolver.resolve(address);
        if (regionCode == null) {
            return Outcome.NO_REGION;   // 추자도·주소 손상 - 지도 권역 밖
        }
        Region region = regionRepository.findByCode(regionCode).orElseThrow();
        PlaceCategory category = categoryRepository.findByCode(categoryCode).orElseThrow();

        Place place = placeRepository.save(Place.builder()
                .region(region)
                .primaryCategory(category)
                .name(item.name())
                .normalizedName(PlaceNameNormalizer.normalize(item.name()))
                .roadAddress(item.roadAddress())
                .lotAddress(item.lotAddress())
                .latitude(item.latitude())
                .longitude(item.longitude())
                .isGoodPrice(false)
                .isHiddenGem(false)
                .reviewCount(0)
                .build());

        DataSource source = dataSourceRepository.findById(SOURCE).orElseThrow();
        mappingRepository.save(PlaceSourceMapping.builder()
                .place(place)
                .source(source)
                .sourcePlaceId(item.bizesId())
                .lastSyncedAt(LocalDateTime.now())
                .build());
        return Outcome.INSERTED;
    }
}
