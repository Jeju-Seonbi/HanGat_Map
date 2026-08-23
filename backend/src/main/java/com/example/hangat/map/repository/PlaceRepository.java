package com.example.hangat.map.repository;

import com.example.hangat.map.model.dto.PlaceListResponse;
import com.example.hangat.map.model.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 장소 조회 - 설계서 §2.1. 네이티브 쿼리 금지(§8.3, 테스트가 H2)라 전부 JPQL이다.
 *
 * <p><b>목록은 DTO 프로젝션, 상세는 fetch join</b>으로 나뉜다.
 * <ul>
 *   <li>목록: 엔티티를 한 건도 로드하지 않으므로 {@code region}·{@code primaryCategory} 지연로딩이 발생할 자리가 없고
 *       (N+1 원천 차단, OSIV 설정에도 안 기댄다), §9.1이 확정한 대로 {@code overview}(TEXT)를 SELECT하지 않는다.</li>
 *   <li>상세: {@code overview}·사진 등 필드가 계속 늘어나는 화면이라 엔티티를 fetch join으로 읽고 DTO가 변환한다.
 *       to-one 두 개는 {@code optional=false}(NOT NULL)라 inner join fetch가 안전하다.</li>
 * </ul>
 *
 * <p><b>distinct 금지.</b> {@code overview}는 H2에서 CLOB이고 H2 2.x는 CLOB에 {@code SELECT DISTINCT}를 거부한다
 * (MariaDB에서는 돌고 테스트에서만 깨진다). 여기 쿼리들은 전부 {@code @ManyToOne} 조인이라 행이 늘지 않아
 * distinct가 애초에 불필요하니 습관적으로도 붙이지 말 것. <b>단서</b>: 이건 to-one에서만 성립한다 -
 * 나중에 {@code @OneToMany}(place_images·reviews)를 join fetch로 붙이면 행이 늘어난다. 그때는 distinct가 아니라
 * 별도 쿼리로 나눠야 한다(상세 Optional이 NonUniqueResult로 깨지는 것도 같은 이유).
 *
 * <p>목록 SELECT 절은 {@link #LIST_SELECT} 한 곳에만 존재한다(문자열 리터럴 결합 = 컴파일타임 상수라 {@code @Query}에 들어간다).
 * 조건이 늘어도 조인·CLOSED 제외를 빠뜨릴 수 없다. 메서드 이름은 파생 쿼리 규칙이 아니라 용도로 지었다 -
 * 넷 다 {@code @Query} 전용이며 부팅 시 {@code em.createQuery}로 검증된다.
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * 목록 SELECT 절. <b>이 인자 순서가 곧 {@link PlaceListResponse} 생성자 시그니처</b>이며
     * 엔티티 필드 선언 순서(주소 → 좌표 → 편의정보)와 같게 맞춰 뒀다 - 다르게 두면 좌표 스왑이 눈에 안 띈다.
     *
     * <p>폐업(CLOSED)만 제외한다: 헛걸음을 만드는 '틀린 정보'라서 '없는 정보'인 UNKNOWN과 성격이 다르다.
     * TEMP_CLOSED·UNKNOWN은 남기고 상태값을 실어 화면이 배지로 표시한다
     * (KTO 적재분 2,138건이 전부 UNKNOWN이라 OPEN만 남기면 통째로 사라진다).
     */
    String LIST_SELECT = """
            select new com.example.hangat.map.model.dto.PlaceListResponse(
                p.id, p.name,
                r.code, r.name, c.code, c.name,
                p.roadAddress, p.lotAddress,
                p.latitude, p.longitude,
                p.phone, p.operatingHoursText,
                p.parkingAvailable, p.toiletAvailable,
                p.businessStatus, p.isGoodPrice, p.isHiddenGem)
            from Place p
                join p.region r
                join p.primaryCategory c
            where p.businessStatus <> com.example.hangat.map.model.enums.BusinessStatus.CLOSED""";

    /** KTO 적재 순서를 유지한다. 이름순은 H2와 MariaDB의 한글 collation이 달라 테스트가 환경마다 흔들린다. */
    String LIST_ORDER = " order by p.id";

    /** type 생략 - 제주 전역 전체. */
    @Query(LIST_SELECT + LIST_ORDER)
    List<PlaceListResponse> findListAll();

    /** type=spot/cafe/stay/cvs/mart. */
    @Query(LIST_SELECT + " and c.code = :categoryCode" + LIST_ORDER)
    List<PlaceListResponse> findListOfCategory(@Param("categoryCode") String categoryCode);

    /** type=food. 착한가격업소는 카테고리가 아니라 플래그다(§2.1). */
    @Query(LIST_SELECT + " and p.isGoodPrice = :goodPrice" + LIST_ORDER)
    List<PlaceListResponse> findListOfGoodPrice(@Param("goodPrice") Boolean goodPrice);

    /** type=dine. */
    @Query(LIST_SELECT + " and c.code = :categoryCode and p.isGoodPrice = :goodPrice" + LIST_ORDER)
    List<PlaceListResponse> findListOfCategoryAndGoodPrice(@Param("categoryCode") String categoryCode,
                                                           @Param("goodPrice") Boolean goodPrice);

    /**
     * 상세. 연관 2개를 한 쿼리에서 초기화한다.
     * 폐업도 그대로 돌려준다 - 찜·공유 링크로 들어오는 경로라 404로 감추면 폐업 사실조차 전달하지 못한다.
     */
    @Query("select p from Place p join fetch p.region join fetch p.primaryCategory where p.id = :id")
    Optional<Place> findDetailById(@Param("id") Long id);
}
