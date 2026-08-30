package com.example.hangat.map.image.model;

/** KTO detailImage2 한 장. 값은 전부 문자열로 온다. */
public record PlaceImageItem(
        String contentid,
        String originimgurl,
        String smallimageurl,
        String imgname,
        String cpyrhtDivCd,
        String serialnum
) {
}
