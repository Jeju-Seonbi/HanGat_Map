package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.CongestionForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 혼잡 예보 조회 - 설계서 §2.2.
 *
 * <p><b>이 테이블은 같은 날짜에 대해 여러 발표 버전이 쌓인다</b>(덮어쓰기 금지가 명세서 요구사항).
 * 그래서 화면용 조회는 <b>반드시 {@code base_at}을 고정</b>해야 한다 - 안 그러면 어제 발표와
 * 오늘 발표가 같이 나와 한 장소가 두 줄로 보인다.
 *
 * <p>배치가 한 번에 전 장소를 넣으므로 <b>전역 최신 {@code base_at} 하나</b>면 충분하다
 * ({@link #findLatestBaseAt()}). 장소별 상관 서브쿼리로 최신을 고르는 방식은 같은 결과를 내면서
 * 훨씬 비싸다.
 */
public interface CongestionForecastRepository extends JpaRepository<CongestionForecast, Long> {

    /** 가장 최근 발표 버전. 데이터가 한 건도 없으면 비어 있다 - 화면은 전부 '정보 없음'이 된다. */
    @Query("select max(f.baseAt) from CongestionForecast f")
    Optional<LocalDateTime> findLatestBaseAt();

    /** 좌측 순위 목록용 - 특정 날짜 한 판. 정렬·필터는 조회 API(4-3)가 얹는다. */
    List<CongestionForecast> findByBaseAtAndForecastAt(LocalDateTime baseAt, LocalDateTime forecastAt);

    /** 상세 화면의 날짜별 예보 22일치. */
    @Query("""
            select f from CongestionForecast f
            where f.place.id = :placeId and f.baseAt = :baseAt
            order by f.forecastAt
            """)
    List<CongestionForecast> findSeriesOf(@Param("placeId") Long placeId,
                                          @Param("baseAt") LocalDateTime baseAt);

    /** 같은 버전을 다시 적재하려는지 판정한다 - 배치를 하루에 두 번 돌렸을 때. */
    boolean existsByBaseAt(LocalDateTime baseAt);
}
