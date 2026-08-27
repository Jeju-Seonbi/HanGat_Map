package com.example.hangat.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 시각 유틸 - UTC로 통일하려고 만듦.
 * LocalDateTime.now()는 서버 시간대를 따라서 KST면 UTC가 아니고 Hibernate도 안 바꿔줌.
 * 엔티티마다 각자 만들면 한 곳만 빠져도 만료 판정이 9시간 어긋남.
 */
public class DateTimes {

    private DateTimes() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
