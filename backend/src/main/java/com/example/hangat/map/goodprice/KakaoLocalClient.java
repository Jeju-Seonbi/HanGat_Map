package com.example.hangat.map.goodprice;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 카카오 로컬 주소 검색 - 주소 문자열을 좌표로 바꾼다 (지오코딩).
 * 착한가격 CSV 에는 좌표가 없어서(주소만) 이 변환이 필요하다.
 * 키는 지도 앱('한갓지도')의 REST 키 - 로그인용 KAKAO_CLIENT_ID 와 다른 앱이다.
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);
    private static final String URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private final RestClient restClient;
    private final String restKey;

    public KakaoLocalClient(@Value("${kakao-local.rest-key:}") String restKey) {
        this.restClient = RestClient.create();
        this.restKey = restKey;
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }

    /** 주소 → 좌표. 못 찾으면 empty - 그 업소는 지도에 못 찍으므로 호출부에서 뺀다 */
    public Optional<GeoPoint> geocode(String address) {
        if (restKey.isBlank()) {
            throw new IllegalStateException("KAKAO_LOCAL_REST_KEY 가 없습니다 - .env 확인");
        }
        try {
            JsonNode body = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(URL)
                            .queryParam("query", address)
                            .build().toUri())
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode docs = body == null ? null : body.path("documents");
            if (docs == null || !docs.isArray() || docs.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = docs.get(0);
            return Optional.of(new GeoPoint(
                    new BigDecimal(first.path("y").asText()),
                    new BigDecimal(first.path("x").asText())));
        } catch (Exception e) {
            log.debug("지오코딩 실패: {} ({})", address, e.getMessage());
            return Optional.empty();
        }
    }
}
