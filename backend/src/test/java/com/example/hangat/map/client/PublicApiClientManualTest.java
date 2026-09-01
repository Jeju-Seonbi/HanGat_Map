package com.example.hangat.map.client;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.map.model.dto.TourApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공공 API 실호출 수동 테스트 - 로컬에서만 실행 (.env의 SERVICE_KEY 필요).
 * CI에는 키도 네트워크도 없으므로 평소에는 @Disabled를 붙여둔다 (팀 WeatherClientManualTest와 같은 방식).
 *
 * <p>기대값은 추측이 아니라 2026-08-23 실측치다 - 제주 전체 2,147건, 관광지(12) 563건.
 * 이 숫자가 달라졌다면 KTO 데이터가 갱신된 것이거나 우리 파라미터가 틀린 것이다.
 */
@Disabled("공공 API 실호출 - 로컬에서 @Disabled 잠시 풀고 수동 실행 (마지막 검증: 2026-08-23 성공)")
class PublicApiClientManualTest {

    /** areaBasedList2 응답 항목 중 검증에 필요한 것만. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceItem(String contentid, String contenttypeid, String title,
                     String addr1, String mapx, String mapy) {
    }

    private PublicApiClient client() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("SERVICE_KEY");
        assertThat(key).as(".env에 SERVICE_KEY가 있어야 한다").isNotBlank();

        PublicApiProperties props = new PublicApiProperties(key, "https://apis.data.go.kr/B551011");
        RestClient rest = new PublicApiClientConfig().publicApiRestClient(props);
        return new PublicApiClient(rest, props, new ObjectMapper());
    }

    @Test
    void 제주_관광정보_전체건수를_가져온다() {
        TourApiResponse<PlaceItem> res = client().get(
                "/KorService2/areaBasedList2",
                Map.of("lDongRegnCd", "50", "numOfRows", 1, "pageNo", 1),
                new TypeReference<>() {
                });

        assertThat(res.resultCode()).isEqualTo("0000");
        // 2026-08-23 실측 2,147건. KTO가 데이터를 늘릴 수 있으므로 하한만 본다
        assertThat(res.totalCount()).isGreaterThanOrEqualTo(2000);

        PlaceItem first = res.items().get(0);
        assertThat(first.title()).isNotBlank();
        // ★ mapx=경도(126.x), mapy=위도(33.x) - 뒤집히면 제주 전역 핀이 통째로 어긋난다
        assertThat(Double.parseDouble(first.mapx())).isBetween(125.0, 127.5);
        assertThat(Double.parseDouble(first.mapy())).isBetween(33.0, 34.0);
    }

    @Test
    void 페이징으로_관광지를_전량_수집한다() {
        List<PlaceItem> items = client().fetchAll(
                "/KorService2/areaBasedList2",
                Map.of("lDongRegnCd", "50", "contentTypeId", "12"),
                new TypeReference<>() {
                });

        // 2026-08-23 실측 563건 (관광지 타입 12)
        assertThat(items).hasSizeGreaterThanOrEqualTo(500);
        assertThat(items).allSatisfy(i -> assertThat(i.contentid()).isNotBlank());
    }

    @Test
    void 집중률도_같은_응답구조로_읽힌다() {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record CrowdItem(String baseYmd, String tAtsNm, String cnctrRate) {
        }

        TourApiResponse<CrowdItem> res = client().get(
                "/TatsCnctrRateService/tatsCnctrRatedList",
                Map.of("areaCd", "50", "signguCd", "50110", "numOfRows", 1, "pageNo", 1),
                new TypeReference<>() {
                });

        assertThat(res.resultCode()).isEqualTo("0000");
        assertThat(res.totalCount()).isGreaterThan(0);
        assertThat(res.items().get(0).cnctrRate()).isNotBlank();
    }

    /**
     * 잘못된 키는 HTTP 403 + {@code OpenAPI_ServiceResponse} 봉투(코드 30)로 온다(2026-08-23 실측).
     * ※ 팀 {@code BaseException}은 상세 문자열을 message가 아니라 {@code result}에 담는다
     *   (message는 항상 BaseResponseStatus의 고정 문구다).
     */
    @Test
    void 잘못된_키는_EXTERNAL_API_ERROR로_변환된다() {
        PublicApiProperties bad = new PublicApiProperties("this-key-does-not-exist", "https://apis.data.go.kr/B551011");
        PublicApiClient badClient = new PublicApiClient(
                new PublicApiClientConfig().publicApiRestClient(bad), bad, new ObjectMapper());

        BaseException thrown = (BaseException) org.assertj.core.api.Assertions.catchThrowable(() -> badClient.get(
                "/KorService2/areaBasedList2",
                Map.of("lDongRegnCd", "50", "numOfRows", 1, "pageNo", 1),
                new TypeReference<TourApiResponse<PlaceItem>>() {
                }));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(BaseResponseStatus.EXTERNAL_API_ERROR);
        // 코드 30 = 미등록 키. 재시도해도 소용없는 오류로 분류돼야 한다
        assertThat(String.valueOf(thrown.getResult()))
                .contains("포털 오류")
                .contains("30")
                .contains(PublicApiErrorCode.KEY_NOT_REGISTERED.name())
                .contains("retryable=false");
    }
}
