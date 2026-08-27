package com.example.hangat.map.service;

import com.example.hangat.map.model.entity.DataSource;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.repository.DataSourceRepository;
import com.example.hangat.map.repository.PlaceCategoryRepository;
import com.example.hangat.map.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 맵 도메인 마스터 코드 데이터 적재 - 설계서 §5.1
 *
 * <p>권역·카테고리·출처는 {@code places.region_id} / {@code primary_category_id} /
 * {@code place_source_mappings.source_code} 가 참조하는 <b>FK 대상</b>이다.
 * 이 행들이 없으면 장소를 한 건도 넣을 수 없어 적재 배치가 시작조차 못 한다.
 * 공공 API가 주지 않는 값이라 우리가 정의한다.
 *
 * <p><b>왜 {@code data.sql}이 아니라 코드인가</b>(2026-08-23 변경):
 * {@code data.sql}은 매 부팅 {@code DELETE} → {@code INSERT}를 돌았는데,
 * {@code places}에 실데이터가 들어간 뒤로는 FK 제약에 걸려 <b>부팅 자체가 실패</b>했다
 * ({@code Cannot delete or update a parent row}).
 * "없으면 넣고 있으면 둔다"로 바꾸면 실데이터가 몇 건이든 안전하고,
 * 운영 환경에 마스터를 수동으로 넣어줄 필요도 없어진다(§10-④가 함께 해소).
 *
 * <p>테스트(H2)에서도 그대로 돈다 - {@code ApplicationRunner}는 컨텍스트가 뜬 뒤 한 번 실행되며,
 * 스키마가 {@code create-drop}이라 매번 비어 있는 상태에서 채워진다.
 */
@Component
public class MapMasterDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MapMasterDataInitializer.class);

    private final RegionRepository regionRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final DataSourceRepository dataSourceRepository;

    public MapMasterDataInitializer(RegionRepository regionRepository,
                                    PlaceCategoryRepository categoryRepository,
                                    DataSourceRepository dataSourceRepository) {
        this.regionRepository = regionRepository;
        this.categoryRepository = categoryRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initRegions();
        initCategories();
        initDataSources();
    }

    /**
     * 제주 관광 관용 4권역. KTO는 제주시/서귀포시 2개만 주므로 자체 정의한다.
     * 권역 판정은 좌표가 아니라 주소의 읍면동으로 한다({@link RegionResolver}).
     *
     * <p>{@code centerLat/Lng}는 KTO 제주 2,147건을 그 규칙으로 분류한 뒤 구한 <b>실측 중앙값</b>이다
     * (2026-08-23). 평균이 아니라 중앙값을 쓴 이유 - 동부는 조천~성산 폭이 넓어 평균이 서쪽으로 끌린다.
     *
     * <p>{@code kmaGrid}는 null로 둔다. 기상청 격자는 날씨 담당(jdh) 몫이고 현재 구현은 제주 전역을
     * 단일 격자로 처리한다. <b>검증 안 된 값을 넣지 않는다</b>(§1.2).
     */
    private void initRegions() {
        if (regionRepository.count() > 0) {
            return;
        }
        regionRepository.saveAll(List.of(
                region("NORTH", "북부", "33.4950010", "126.5169050", (byte) 1),
                region("EAST", "동부", "33.4592800", "126.7843930", (byte) 2),
                region("SOUTH", "남부", "33.2487280", "126.5142330", (byte) 3),
                region("WEST", "서부", "33.3434170", "126.3124120", (byte) 4)
        ));
        log.info("권역 마스터 4행 적재");
    }

    /**
     * 서로 다른 두 출처를 하나로 모으는 사전이다.
     * KTO는 {@code contenttypeid}(숫자), 소상공인은 {@code indsSclsCd}(문자)를 주는데 둘 다 이 코드로 모인다.
     *
     * <p>SHOPPING은 화면 필터에 없지만 KTO 제주 쇼핑 395건을 버리지 않으려고 둔다 -
     * 나중에 필터를 추가하면 바로 쓸 수 있다.
     */
    private void initCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }
        categoryRepository.saveAll(List.of(
                category("TOURIST", "관광지", (short) 1),
                category("FOOD", "음식점", (short) 2),
                category("CAFE", "카페", (short) 3),
                category("LODGING", "숙소", (short) 4),
                category("CONVENIENCE", "편의점", (short) 5),
                category("MART", "마트", (short) 6),
                category("SHOPPING", "쇼핑", (short) 7)
        ));
        log.info("장소 카테고리 마스터 7행 적재");
    }

    /**
     * {@code attributionText}는 화면 푸터의 출처 표기에 그대로 쓴다 - 공공데이터 이용 조건이자 심사 확인 항목이다.
     * {@code disclaimerText}는 '예측값'인 출처에 특히 필요하다 - 집중률은 실측이 아니라 예측이고,
     * 그 사실을 숨기면 데이터 정직성 원칙(§1.2)에 어긋난다.
     */
    private void initDataSources() {
        if (dataSourceRepository.count() > 0) {
            return;
        }
        dataSourceRepository.saveAll(List.of(
                DataSource.builder()
                        .code("KTO")
                        .displayName("한국관광공사 국문 관광정보")
                        .providerName("한국관광공사")
                        .homepageUrl("https://knto.or.kr")
                        .apiUrl("https://www.data.go.kr/data/15101578/openapi.do")
                        .licenseName("공공누리 제1유형")
                        .licenseUrl("https://www.kogl.or.kr/info/license.do")
                        .attributionText("출처: 한국관광공사 국문 관광정보 서비스")
                        .displayOrder((short) 1)
                        .build(),
                DataSource.builder()
                        .code("KTO_CNCTR")
                        .displayName("한국관광공사 관광지 집중률 예측")
                        .providerName("한국관광공사")
                        .homepageUrl("https://knto.or.kr")
                        .apiUrl("https://www.data.go.kr/data/15128555/openapi.do")
                        .licenseName("공공누리 제1유형")
                        .licenseUrl("https://www.kogl.or.kr/info/license.do")
                        .attributionText("출처: 한국관광공사 관광지 집중률 방문자 추이 예측 정보")
                        .disclaimerText("집중률은 실측 방문자 수가 아니라 예측값이며, 각 관광지의 최성수기를 100으로 본 상대 지수입니다. "
                                + "관광지 간 절대 비교에는 쓸 수 없습니다.")
                        .displayOrder((short) 2)
                        .build(),
                DataSource.builder()
                        .code("SBIZ")
                        .displayName("소상공인시장진흥공단 상가정보")
                        .providerName("소상공인시장진흥공단")
                        .homepageUrl("https://www.semas.or.kr")
                        .apiUrl("https://www.data.go.kr/data/15012005/openapi.do")
                        .licenseName("공공누리 제1유형")
                        .licenseUrl("https://www.kogl.or.kr/info/license.do")
                        .attributionText("출처: 소상공인시장진흥공단 상가(상권)정보")
                        .disclaimerText("상가 정보는 수집 시점 기준이며 폐업·이전이 반영되지 않았을 수 있습니다.")
                        .displayOrder((short) 3)
                        .build(),
                DataSource.builder()
                        .code("MOIS_GOODPRICE")
                        .displayName("제주시 착한가격업소")
                        .providerName("제주시")
                        .homepageUrl("https://www.jejusi.go.kr")
                        .apiUrl("https://www.data.go.kr/data/15109183/openapi.do")
                        .licenseName("공공누리 제1유형")
                        .licenseUrl("https://www.kogl.or.kr/info/license.do")
                        .attributionText("출처: 제주시 착한가격업소 정보")
                        .disclaimerText("착한가격업소 지정 현황은 기준일자 기준이며 이후 변경될 수 있습니다.")
                        .displayOrder((short) 4)
                        .build()
        ));
        log.info("데이터 출처 마스터 4행 적재");
    }

    private Region region(String code, String name, String lat, String lng, byte order) {
        return Region.builder()
                .code(code)
                .name(name)
                .centerLat(new BigDecimal(lat))
                .centerLng(new BigDecimal(lng))
                .displayOrder(order)
                .build();
    }

    private PlaceCategory category(String code, String name, short order) {
        return PlaceCategory.builder()
                .code(code)
                .name(name)
                .displayOrder(order)
                .build();
    }
}
