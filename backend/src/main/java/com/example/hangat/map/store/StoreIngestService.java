package com.example.hangat.map.store;

import com.example.hangat.map.store.SbizStoreClient.StoreItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카페·편의점·마트 적재 (MAP-04 나머지) - 소상공인 상가정보 → places.
 * 좌표가 응답에 포함돼 있어 지오코딩이 필요 없다(착한가격과 다른 점).
 */
@Service
public class StoreIngestService {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestService.class);

    /** 업종 소분류 → 우리 카테고리 (실측: 카페 3,048 / 편의점 1,324 / 슈퍼마켓 1,067) */
    private static final Map<String, String> UPJONG_TO_CATEGORY = Map.of(
            "I21201", "CAFE",
            "G20405", "CONVENIENCE",
            "G20404", "MART");

    private final SbizStoreClient client;
    private final StoreIngestWriter writer;

    public StoreIngestService(SbizStoreClient client, StoreIngestWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    public record StoreIngestResult(Map<String, Integer> fetched, int inserted, int unchanged,
                                    int skippedNoCoord, int skippedRegion) {
    }

    public StoreIngestResult ingest() {
        Map<String, Integer> fetched = new LinkedHashMap<>();
        int inserted = 0;
        int unchanged = 0;
        int noCoord = 0;
        int noRegion = 0;

        for (Map.Entry<String, String> e : UPJONG_TO_CATEGORY.entrySet()) {
            List<StoreItem> items = client.fetchAll(e.getKey());
            fetched.put(e.getValue(), items.size());
            for (StoreItem item : items) {
                if (item.latitude() == null || item.longitude() == null) {
                    noCoord++;
                    continue;
                }
                switch (writer.upsert(item, e.getValue())) {
                    case INSERTED -> inserted++;
                    case ALREADY -> unchanged++;
                    case NO_REGION -> noRegion++;
                }
            }
        }
        StoreIngestResult result = new StoreIngestResult(fetched, inserted, unchanged, noCoord, noRegion);
        log.info("소상공인 적재 완료 {}", result);
        return result;
    }
}
