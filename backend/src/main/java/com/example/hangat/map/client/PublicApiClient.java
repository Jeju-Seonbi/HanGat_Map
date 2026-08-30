package com.example.hangat.map.client;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.map.model.dto.TourApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국관광공사 계열 공공 API 호출 공통부 - 설계서 §3.4
 *
 * <p>KorService2·집중률·방문자수가 같은 호스트·같은 응답 구조를 쓰므로 호출부를 하나로 묶는다.
 * 각 배치는 경로와 파라미터만 넘기고, 인증키·공통 파라미터·에러 판정·페이징은 여기서 처리한다.
 *
 * <p><b>응답을 문자열로 먼저 받는 이유</b>: 포털 레벨 오류는 서비스 응답과 <b>모양이 다른 봉투</b>
 * ({@code OpenAPI_ServiceResponse})로 오고, {@code _type=json}을 붙여도 XML로 오는 경우가 있다.
 * 곧바로 객체로 파싱하면 엉뚱한 파싱 예외가 나서 원인이 사라지므로,
 * 문자열 상태에서 봉투를 먼저 판정한 뒤 파싱한다.
 *
 * <p><b>serviceKey 인코딩</b>: URI 템플릿 변수로 넘겨 Spring이 <b>한 번만</b> 인코딩하게 한다
 * (jdh의 {@code WeatherClient}와 같은 방식). 그래서 설정에는 Decoding 키를 넣어야 한다.
 */
@Component
public class PublicApiClient {

    private static final Logger log = LoggerFactory.getLogger(PublicApiClient.class);

    /** 포털 레벨 오류 봉투. XML·JSON 어느 쪽으로 와도 이 문자열이 들어 있다. */
    private static final String PORTAL_ERROR_ENVELOPE = "OpenAPI_ServiceResponse";
    private static final Pattern REASON_CODE = Pattern.compile("returnReasonCode\"?\\s*[>:]\\s*\"?(\\d+)");
    private static final Pattern ERR_MSG = Pattern.compile("errMsg\"?\\s*[>:]\\s*\"?([A-Z_]+)");

    /** 서비스 레벨 성공 코드. */
    private static final String OK = "0000";

    /** 포털이 허용하는 한 페이지 최대치. 실측으로 1000까지 정상 동작 확인(2026-08-23). */
    public static final int MAX_PAGE_SIZE = 1000;

    /** 페이징 폭주 방지. 제주 전역이 2,147건이라 여유 있는 상한이다. */
    private static final int MAX_PAGES = 50;

    private final RestClient restClient;
    private final PublicApiProperties properties;
    private final ObjectMapper objectMapper;

    public PublicApiClient(RestClient publicApiRestClient,
                           PublicApiProperties properties,
                           ObjectMapper objectMapper) {
        this.restClient = publicApiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 한 페이지 조회.
     *
     * @param path   {@code tour-base-url} 이후 경로 (예: {@code /KorService2/areaBasedList2})
     * @param params 서비스별 파라미터. 인증키·MobileOS·_type 등 공통값은 넣지 않아도 된다
     * @param type   항목 타입 (예: {@code new TypeReference<TourApiResponse<PlaceItem>>() {}})
     */
    public <T> TourApiResponse<T> get(String path,
                                      Map<String, Object> params,
                                      TypeReference<TourApiResponse<T>> type) {
        String raw = callRaw(path, params);
        TourApiResponse<T> parsed = parse(raw, type, path);
        verifyResultCode(parsed, path);
        return parsed;
    }

    /**
     * {@code totalCount}를 보고 끝까지 받아온다.
     *
     * <p>중간에 실패하면 예외가 올라간다 - <b>부분 결과를 성공처럼 돌려주지 않는다.</b>
     * 반쯤 받은 데이터로 DB를 덮어쓰면 멀쩡하던 캐시가 망가지기 때문이다(설계서 §7).
     */
    public <T> List<T> fetchAll(String path,
                                Map<String, Object> params,
                                TypeReference<TourApiResponse<T>> type) {
        List<T> all = new ArrayList<>();
        int page = 1;
        int total = -1;

        while (page <= MAX_PAGES) {
            Map<String, Object> paged = new LinkedHashMap<>(params);
            paged.put("numOfRows", MAX_PAGE_SIZE);
            paged.put("pageNo", page);

            TourApiResponse<T> res = get(path, paged, type);
            List<T> items = res.items();
            all.addAll(items);

            if (total < 0) {
                total = res.totalCount();
            }
            if (items.isEmpty() || all.size() >= total) {
                break;
            }
            page++;
        }

        if (page > MAX_PAGES) {
            log.warn("페이징 상한({}p) 도달 - 일부만 수집했다. path={} 수집={} 전체={}",
                    MAX_PAGES, path, all.size(), total);
        }
        log.info("공공 API 수집 완료 path={} 수집={}건 (totalCount={})", path, all.size(), total);
        return all;
    }

    // ── 내부 ─────────────────────────────────────────────────────────

    /** 공통 파라미터를 붙여 호출하고 본문을 문자열 그대로 돌려준다. */
    private String callRaw(String path, Map<String, Object> params) {
        try {
            return restClient.get()
                    .uri(builder -> applyParams(builder.path(path), params)
                            // 값은 템플릿 변수로 넘겨 Spring이 한 번만 인코딩하게 한다
                            .queryParam("serviceKey", "{serviceKey}")
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "hangat")
                            .queryParam("_type", "json")
                            .build(properties.serviceKey()))
                    .retrieve()
                    // 4xx·5xx여도 본문을 읽어야 한다. 포털은 오류 사유를 본문에 담아 보낸다
                    .onStatus(status -> true, (req, res) -> { })
                    .body(String.class);
        } catch (Exception e) {
            throw new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR,
                    "공공 API 호출 실패: " + path + " (" + e.getMessage() + ")");
        }
    }

    private UriBuilder applyParams(UriBuilder builder, Map<String, Object> params) {
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() != null) {
                    builder = builder.queryParam(e.getKey(), e.getValue());
                }
            }
        }
        return builder;
    }

    /**
     * 파싱 전에 포털 레벨 오류를 먼저 걸러낸다.
     * 이 판정을 건너뛰면 오류 봉투가 서비스 응답 타입에 안 맞아 엉뚱한 파싱 예외로 둔갑한다.
     */
    private <T> TourApiResponse<T> parse(String raw, TypeReference<TourApiResponse<T>> type, String path) {
        if (raw == null || raw.isBlank()) {
            throw new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR, "공공 API 응답이 비었다: " + path);
        }
        verifyNotPortalError(raw, path);
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            String head = raw.length() > 200 ? raw.substring(0, 200) : raw;
            throw new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR,
                    "공공 API 응답 파싱 실패: " + path + " / " + head);
        }
    }

    private void verifyNotPortalError(String raw, String path) {
        if (!raw.contains(PORTAL_ERROR_ENVELOPE)) {
            return;
        }
        String reason = find(REASON_CODE, raw);
        String msg = find(ERR_MSG, raw);
        PublicApiErrorCode code = PublicApiErrorCode.from(reason);

        // 22는 재시도하면 다음날 쿼터까지 태운다. 호출자가 구분할 수 있게 로그를 남긴다
        if (code == PublicApiErrorCode.QUOTA_EXCEEDED) {
            log.error("일일 트래픽 초과(22) - 배치를 중단하고 이전 캐시를 유지한다. path={}", path);
        } else {
            log.warn("공공 API 포털 오류 code={} msg={} path={} retryable={}", reason, msg, path, code.isRetryable());
        }
        throw new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR,
                "포털 오류 " + reason + " " + msg + " (" + code.name() + ", retryable=" + code.isRetryable() + ")");
    }

    private <T> void verifyResultCode(TourApiResponse<T> res, String path) {
        String code = res.resultCode();
        if (!OK.equals(code)) {
            throw new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR,
                    "공공 API 서비스 오류 resultCode=" + code + " " + res.resultMsg() + " path=" + path);
        }
    }

    private String find(Pattern p, String raw) {
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "?";
    }

    /** 배치가 직접 URI를 조립해야 할 때 쓰는 탈출구. 되도록 {@link #get}을 쓴다. */
    public String callRawWith(Function<UriBuilder, UriBuilder> customizer) {
        return restClient.get()
                .uri(builder -> customizer.apply(builder)
                        .queryParam("serviceKey", "{serviceKey}")
                        .build(properties.serviceKey()))
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .body(String.class);
    }
}
