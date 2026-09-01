package com.example.hangat.map.goodprice;

import com.example.hangat.map.goodprice.GoodPriceCsv.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 착한가격 CSV 파싱 - 실파일(393행) 기준 검증. 수치는 W0 실측 분석과 대조한다 */
class GoodPriceCsvTest {

    private List<Row> load() {
        return GoodPriceCsv.parse(getClass().getResourceAsStream("/data/goodprice_jeju.csv"));
    }

    @Test
    void 실파일_393행이_전부_읽힌다() {
        List<Row> rows = load();

        assertThat(rows).hasSize(393);
        // 주소에 콤마가 든 행("...하귀9길 2, 1층 114호")도 밀리지 않아야 한다
        Row 강김밥 = rows.stream().filter(r -> r.name().equals("강김밥집")).findFirst().orElseThrow();
        assertThat(강김밥.address()).contains("하귀9길 2").contains("114호");
        assertThat(강김밥.sigun()).isEqualTo("제주시");
    }

    @Test
    void 외식업_분류가_W0_실측과_같다() {
        List<Row> rows = load();

        long food = rows.stream().filter(r -> GoodPriceCsv.isFood(r.category())).count();
        // W0 분석 실측: 외식 284 / 비외식 109
        assertThat(food).isEqualTo(284);
        assertThat(GoodPriceCsv.isFood("한식")).isTrue();
        assertThat(GoodPriceCsv.isFood("기타요식업")).isTrue();
        assertThat(GoodPriceCsv.isFood("미용업")).isFalse();
        assertThat(GoodPriceCsv.isFood("기타비요식업")).isFalse();
        assertThat(GoodPriceCsv.isFood("숙박업")).isFalse();
    }

    @Test
    void 대표메뉴_문구는_최대_2개를_원문_가격으로_만든다() {
        List<Row> rows = load();

        Row 강김밥 = rows.stream().filter(r -> r.name().equals("강김밥집")).findFirst().orElseThrow();
        assertThat(강김밥.menuText()).startsWith("대표메뉴: ").contains("강김밥 2,900원");
        // 메뉴가 하나도 없으면 문구를 만들지 않는다 - 빈 소개글을 강요하지 않기
        assertThat(new Row("제주시", "한식", "x", null, "주소", List.of()).menuText()).isNull();
    }

}
