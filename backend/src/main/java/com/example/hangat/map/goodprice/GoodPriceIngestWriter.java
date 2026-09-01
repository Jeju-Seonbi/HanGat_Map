package com.example.hangat.map.goodprice;

import com.example.hangat.map.goodprice.GoodPriceCsv.Row;
import com.example.hangat.map.goodprice.KakaoLocalClient.GeoPoint;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/** 착한가격 한 건 저장. 기준일은 CSV 배포 기준(2026-06-30, 차기 갱신 10-30). */
@Component
public class GoodPriceIngestWriter {

    static final String SOURCE = "MOIS_GOODPRICE";
    static final LocalDate BASE_DATE = LocalDate.of(2026, 6, 30);

    private final PlaceRepository placeRepository;
    private final PlaceSourceMappingRepository mappingRepository;
    private final DataSourceRepository dataSourceRepository;
    private final RegionRepository regionRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final RegionResolver regionResolver;

    public GoodPriceIngestWriter(PlaceRepository placeRepository,
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

    public enum Outcome { ALREADY, MATCHED, NONE }

    /**
     * 이미 적재했으면 ALREADY(멱등), 기존 KTO 장소와 같은 가게면 플래그만 켜고 MATCHED.
     * 이름이 같아도 주소 읍면동이 다르면 다른 가게다 - 신규(NONE)로 보낸다.
     */
    @Transactional
    public Outcome upsertMatched(Row row) {
        String sourceId = sourceIdOf(row);
        if (mappingRepository.findBySourceCodeAndSourcePlaceId(SOURCE, sourceId).isPresent()) {
            return Outcome.ALREADY;
        }
        String normalized = PlaceNameNormalizer.normalize(row.name());
        List<Place> candidates = placeRepository.findByNormalizedName(normalized);
        for (Place place : candidates) {
            if (sameTown(place.getRoadAddress(), row.address())
                    || sameTown(place.getLotAddress(), row.address())) {
                place.markGoodPrice(BASE_DATE, row.menuText(), row.phone());
                saveMapping(place, sourceId, row);
                return Outcome.MATCHED;
            }
        }
        return Outcome.NONE;
    }

    /** 신규 삽입. 권역 판정 불가(추자도 등)면 false. */
    @Transactional
    public boolean insertNew(Row row, GeoPoint geo) {
        String regionCode = regionResolver.resolve(row.address());
        if (regionCode == null) {
            return false;
        }
        Region region = regionRepository.findByCode(regionCode).orElseThrow();
        PlaceCategory food = categoryRepository.findByCode("FOOD").orElseThrow();

        Place place = placeRepository.save(Place.builder()
                .region(region)
                .primaryCategory(food)
                .name(row.name())
                .normalizedName(PlaceNameNormalizer.normalize(row.name()))
                .roadAddress(row.address())
                .latitude(geo.latitude())
                .longitude(geo.longitude())
                .phone(row.phone())
                .overview(row.menuText())
                .isGoodPrice(true)
                .goodPriceBaseDate(BASE_DATE)
                .isHiddenGem(false)
                .reviewCount(0)
                .build());
        saveMapping(place, sourceIdOf(row), row);
        return true;
    }

    private void saveMapping(Place place, String sourceId, Row row) {
        DataSource source = dataSourceRepository.findById(SOURCE).orElseThrow();
        mappingRepository.save(PlaceSourceMapping.builder()
                .place(place)
                .source(source)
                .sourcePlaceId(sourceId)
                .rawPayload("{\"name\":\"" + row.name() + "\",\"address\":\"" + row.address() + "\"}")
                .lastSyncedAt(LocalDateTime.now())
                .build());
    }

    /** CSV 에 고유 ID 가 없어 업소명|주소 해시를 쓴다 - 재실행 멱등의 키 */
    static String sourceIdOf(Row row) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest((row.name() + "|" + row.address()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 같은 읍·면·동에 있으면 같은 가게로 본다 (이름 동일 전제) */
    static boolean sameTown(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String townA = townOf(a);
        return !townA.isEmpty() && townA.equals(townOf(b));
    }

    private static String townOf(String address) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([가-힣]+(읍|면|동))").matcher(address);
        return m.find() ? m.group(1) : "";
    }
}
