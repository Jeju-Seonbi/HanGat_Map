package com.example.hangat.course.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 코스 기간 표기 - 카드와 상세가 같은 문구를 쓰도록 한 곳에 둔다.
 *
 * <p>ChronoUnit인 이유: 연말 경계(12/31 → 1/1)에서 dayOfYear 뺄셈은 음수가 된다.
 */
public record CourseDuration(int days, String text) {

    public static CourseDuration between(LocalDate startDate, LocalDate endDate) {
        int days = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return new CourseDuration(days, text(days));
    }

    /** 하루짜리는 "0박 1일"이 아니라 "당일치기"다 - 생성 API가 start==end를 허용한다. */
    private static String text(int days) {
        if (days <= 1) {
            return "당일치기";
        }
        return (days - 1) + "박 " + days + "일";
    }
}
