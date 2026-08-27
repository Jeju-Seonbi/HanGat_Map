package com.example.hangat.map.model.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link PlaceTag}의 복합 PK - 테이블 명세서 13.0 {@code PK(place_id, tag_id)}
 *
 * <p>필드 이름이 {@code PlaceTag}의 연관 필드 이름({@code place}, {@code tag})과 <b>같아야</b>
 * {@code @IdClass}가 짝을 찾는다. 타입은 각 대상 엔티티의 {@code @Id} 타입이다.
 *
 * <p>이 복합 PK가 곧 <b>같은 장소에 같은 태그가 두 번 붙는 것을 막는 제약</b>이다.
 * 배치를 몇 번 돌려도 태그가 중복되지 않는다.
 */
public class PlaceTagId implements Serializable {

    private Long place;
    private Short tag;

    protected PlaceTagId() {
    }

    public PlaceTagId(Long place, Short tag) {
        this.place = place;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlaceTagId other)) {
            return false;
        }
        return Objects.equals(place, other.place) && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(place, tag);
    }
}
