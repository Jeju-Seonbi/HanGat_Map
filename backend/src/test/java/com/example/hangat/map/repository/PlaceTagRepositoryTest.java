package com.example.hangat.map.repository;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceCategory;
import com.example.hangat.map.model.entity.PlaceTag;
import com.example.hangat.map.model.entity.Region;
import com.example.hangat.map.model.entity.Tag;
import com.example.hangat.map.model.enums.TagSourceType;
import com.example.hangat.map.model.enums.TagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세부분류 태그 - 복합 PK 제약과 재적재 시 삭제 범위 검증.
 *
 * <p><b>두 번째 테스트가 이 커밋의 핵심이다.</b> 재적재는 기존 태그를 지우고 다시 붙이는데,
 * 지우는 범위가 넓으면 <b>운영자가 손으로 붙인 태그가 배치마다 조용히 사라진다</b>.
 * 에러도 안 나고 로그도 안 남아서, 며칠 뒤 "왜 태그가 없지"로만 드러난다.
 *
 * <p>{@code replace = NONE} 사유는 {@link PlaceRepositoryTest} 참고.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PlaceTagRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PlaceTagRepository placeTagRepository;

    private Place 금오름;
    private Place 협재해수욕장;
    private Tag 오름;
    private Tag 해수욕장;

    @BeforeEach
    void setUp() {
        Region west = Region.builder().code("WEST").name("서부").displayOrder((byte) 1).build();
        PlaceCategory tourist = PlaceCategory.builder().code("TOURIST").name("관광지").build();
        em.persist(west);
        em.persist(tourist);

        금오름 = persistPlace(west, tourist, "금오름", "33.3560000", "126.3060000");
        협재해수욕장 = persistPlace(west, tourist, "협재해수욕장", "33.3940000", "126.2400000");

        오름 = persistTag("NA010100", "산, 고개, 오름, 봉우리", "자연관광 > 자연경관");
        해수욕장 = persistTag("NA020100", "해수욕장", "자연관광 > 해양경관");
        em.flush();
    }

    @Test
    void 같은_장소_같은_태그를_다시_저장해도_행이_늘지_않는다() {
        placeTagRepository.saveAndFlush(PlaceTag.fromApi(금오름, 오름));
        em.clear();

        placeTagRepository.saveAndFlush(PlaceTag.fromApi(금오름, 오름));
        em.flush();
        em.clear();

        // 복합 PK 덕분에 배치를 몇 번 돌려도 태그가 불어나지 않는다.
        // 단, 막히는 방식이 예외가 아니다 - save()는 PK가 채워진 엔티티를 '새 행'으로 보지 않아
        // INSERT가 아니라 merge(UPDATE)로 흐른다. 그래서 조용히 덮어써질 뿐 오류는 안 난다.
        assertThat(placeTagRepository.findAll()).hasSize(1);
    }

    @Test
    void 재적재_삭제는_API_태그만_지우고_운영자_태그는_남긴다() {
        placeTagRepository.save(PlaceTag.fromApi(금오름, 오름));
        placeTagRepository.save(PlaceTag.builder()
                .place(금오름).tag(해수욕장)
                .weight(BigDecimal.ONE).sourceType(TagSourceType.ADMIN)
                .build());
        // 다른 장소의 API 태그까지 지우면 안 된다
        placeTagRepository.save(PlaceTag.fromApi(협재해수욕장, 해수욕장));
        em.flush();

        placeTagRepository.deleteByPlaceAndSourceType(금오름, TagSourceType.API);
        em.flush();
        em.clear();

        List<PlaceTag> remaining = placeTagRepository.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining)
                .extracting(pt -> pt.getPlace().getName() + "/" + pt.getTag().getCode() + "/" + pt.getSourceType())
                .containsExactlyInAnyOrder("금오름/NA020100/ADMIN", "협재해수욕장/NA020100/API");
    }

    @Test
    void 드롭다운은_장소가_붙은_태그만_많은_순으로_준다() {
        placeTagRepository.save(PlaceTag.fromApi(금오름, 오름));
        placeTagRepository.save(PlaceTag.fromApi(협재해수욕장, 오름));
        em.flush();
        em.clear();

        List<Object[]> rows = placeTagRepository.countPlacesPerTag();

        // 아무 장소도 안 붙은 '해수욕장'은 나오지 않는다 - 제주에 없는 분류를 걸러내는 장치
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("NA010100");
        assertThat(rows.get(0)[1]).isEqualTo("산, 고개, 오름, 봉우리");
        assertThat(((Number) rows.get(0)[2]).intValue()).isEqualTo(2);
    }

    @Test
    void API_태그의_가중치는_1이다() {
        placeTagRepository.save(PlaceTag.fromApi(금오름, 오름));
        em.flush();
        em.clear();

        PlaceTag found = placeTagRepository.findAll().get(0);
        // DECIMAL(5,4)라 조회 시 1.0000으로 돌아온다 - equals가 아니라 값 비교여야 한다
        assertThat(found.getWeight()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(found.getSourceType()).isEqualTo(TagSourceType.API);
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    private Place persistPlace(Region region, PlaceCategory category, String name, String lat, String lng) {
        Place place = Place.builder()
                .region(region).primaryCategory(category)
                .name(name).normalizedName(name)
                .latitude(new BigDecimal(lat)).longitude(new BigDecimal(lng))
                .build();
        em.persist(place);
        return place;
    }

    private Tag persistTag(String code, String name, String description) {
        Tag tag = Tag.builder()
                .code(code).name(name).description(description)
                .tagType(TagType.PLACE).isActive(true)
                .build();
        em.persist(tag);
        return tag;
    }
}
