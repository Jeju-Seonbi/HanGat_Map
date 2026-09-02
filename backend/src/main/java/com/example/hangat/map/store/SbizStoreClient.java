package com.example.hangat.map.store;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 소상공인 상가(상권)정보 - 시도(제주=50)+업종 소분류로 전 매장을 페이징 수집한다.
 * 활용신청은 공용 공공데이터 계정으로 했다 - Decoding 키인 SERVICE_KEY(public-api)만 통하고 TOUR_API_SERVICE_KEY는 403(실측).
 * 페이지당 1,000건이라 제주 3업종(~5,400곳)은 8콜이면 끝난다(실측).
 */
@Component
public class SbizStoreClient {

    private static final Logger log = LoggerFactory.getLogger(SbizStoreClient.class);
    private static final String URL = "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong";
    private static final String JEJU = "50";
    private static final int PAGE_SIZE = 1000;

    private final RestClient restClient;
    private final String serviceKey;

    public SbizStoreClient(@Value("${public-api.service-key}") String serviceKey) {
        this.restClient = RestClient.create();
        this.serviceKey = serviceKey;
    }

    /** 상가 한 곳. bizesId 가 공단의 고유 ID 라 재적재 멱등의 키가 된다 */
    public record StoreItem(String bizesId, String name, String roadAddress, String lotAddress,
                            BigDecimal latitude, BigDecimal longitude) {
    }

    public List<StoreItem> fetchAll(String indsSclsCd) {
        List<StoreItem> all = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode body = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(URL)
                            .queryParam("serviceKey", "{key}")
                            .queryParam("type", "json")
                            .queryParam("divId", "ctprvnCd")
                            .queryParam("key", JEJU)
                            .queryParam("indsSclsCd", indsSclsCd)
                            .queryParam("numOfRows", PAGE_SIZE)
                            .queryParam("pageNo", page)
                            .encode().buildAndExpand(serviceKey).toUri())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode items = body == null ? null : body.path("body").path("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode it : items) {
                all.add(new StoreItem(
                        it.path("bizesId").asText(null),
                        it.path("bizesNm").asText(null),
                        blankToNull(it.path("rdnmAdr").asText(null)),
                        blankToNull(it.path("lnoAdr").asText(null)),
                        decimal(it.path("lat")),
                        decimal(it.path("lon"))));
            }
            int total = body.path("body").path("totalCount").asInt(0);
            log.info("소상공인 수집 {}: {}페이지, 누적 {}/{}", indsSclsCd, page, all.size(), total);
            if (all.size() >= total || items.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return all;
    }

    private static BigDecimal decimal(JsonNode n) {
        return n.isMissingNode() || n.asText().isBlank() ? null : new BigDecimal(n.asText());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
