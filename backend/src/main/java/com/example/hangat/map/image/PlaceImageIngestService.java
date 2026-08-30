package com.example.hangat.map.image;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.map.client.PublicApiClient;
import com.example.hangat.map.image.model.PlaceImageItem;
import com.example.hangat.map.model.dto.TourApiResponse;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.repository.DataSourceRepository;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.map.repository.PlaceSourceMappingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 장소 사진 적재 (MAP-08) - KTO detailImage2, 장소당 1콜.
 * 쿼터 분할·이어하기·쿼터 초과 시 즉시 중단은 상세 적재(PlaceDetailIngestService)와 같다.
 */
@Service
public class PlaceImageIngestService {

    private static final Logger log = LoggerFactory.getLogger(PlaceImageIngestService.class);

    private static final String PATH = "/KorService2/detailImage2";
    public static final int DEFAULT_LIMIT = 900;
    private static final int CHUNK = 100;

    private final PublicApiClient client;
    private final PlaceRepository placeRepository;
    private final PlaceSourceMappingRepository mappingRepository;
    private final DataSourceRepository dataSourceRepository;
    private final PlaceImageIngestWriter writer;

    public PlaceImageIngestService(PublicApiClient client,
                                   PlaceRepository placeRepository,
                                   PlaceSourceMappingRepository mappingRepository,
                                   DataSourceRepository dataSourceRepository,
                                   PlaceImageIngestWriter writer) {
        this.client = client;
        this.placeRepository = placeRepository;
        this.mappingRepository = mappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.writer = writer;
    }

    /**
     * @param empty     KTO에 사진이 0장인 장소 수 (다음 배치에서 다시 조회된다)
     * @param remaining 아직 사진이 없는 장소 수. 0이 될 때까지 다시 실행하면 된다
     */
    public record ImageIngestResult(int requested, int called, int updated, int savedImages,
                                    int empty, int skippedNoSourceId, int failed,
                                    int remaining, boolean quotaExceeded) {
    }

    public ImageIngestResult ingest(int limit) {
        String attribution = dataSourceRepository.findById("KTO")
                .map(ds -> ds.getAttributionText()).orElse("출처: 한국관광공사");

        List<Place> targets = placeRepository.findWithoutImage(PageRequest.of(0, limit));
        log.info("사진 적재 시작: 대상 {}건 (limit={})", targets.size(), limit);

        List<PlaceImageIngestWriter.Row> rows = new ArrayList<>();
        int called = 0;
        int noSourceId = 0;
        int failed = 0;
        int empty = 0;
        boolean quotaExceeded = false;

        for (Place place : targets) {
            String contentId = mappingRepository
                    .findByPlaceIdAndSourceCode(place.getId(), "KTO")
                    .map(m -> m.getSourcePlaceId())
                    .orElse(null);
            if (contentId == null) {
                noSourceId++;
                continue;
            }

            try {
                TourApiResponse<PlaceImageItem> res = client.get(PATH,
                        Map.of("contentId", contentId),
                        new TypeReference<TourApiResponse<PlaceImageItem>>() {
                        });
                called++;
                List<PlaceImageItem> items = res.items();
                if (items.isEmpty()) {
                    empty++;
                } else {
                    rows.add(new PlaceImageIngestWriter.Row(place.getId(), items));
                }
            } catch (BaseException e) {
                // 쿼터 초과면 즉시 중단 - 사유는 PlaceDetailIngestService 참고
                if (String.valueOf(e.getResult()).contains("QUOTA_EXCEEDED")) {
                    log.error("일일 트래픽 초과 - 중단하고 지금까지 받은 것만 저장한다");
                    quotaExceeded = true;
                    break;
                }
                failed++;
                log.debug("사진 조회 실패 place={} contentId={}", place.getName(), contentId);
            }
        }

        int updated = 0;
        int saved = 0;
        for (int i = 0; i < rows.size(); i += CHUNK) {
            PlaceImageIngestWriter.ChunkResult r =
                    writer.saveChunk(rows.subList(i, Math.min(i + CHUNK, rows.size())), attribution);
            updated += r.updated();
            saved += r.saved();
        }

        int remaining = placeRepository.findWithoutImage(PageRequest.of(0, Integer.MAX_VALUE)).size();
        ImageIngestResult result = new ImageIngestResult(
                targets.size(), called, updated, saved, empty, noSourceId, failed, remaining, quotaExceeded);
        log.info("사진 적재 완료 {}", result);
        return result;
    }
}
