package com.example.hangat.course.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 스케줄러 트리거 가드 - 기동 옵션·KST 내일 계산·예외 삼킴(부팅 보호). */
class SampleCourseSchedulerTest {

    private final SampleCourseGenerator generator = mock(SampleCourseGenerator.class);
    private final LocalDate 내일 = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    @Test
    void 야간_배치는_KST_기준_내일_출발로_돈다() {
        new SampleCourseScheduler(generator, false).nightly();

        verify(generator).generate(내일);
    }

    @Test
    void 기동_생성은_옵션이_꺼져_있으면_돌지_않는다() {
        new SampleCourseScheduler(generator, false).onStartup();

        verify(generator, never()).generate(any());
    }

    @Test
    void 기동_생성의_예외는_부팅을_죽이지_않는다() {
        given(generator.generate(any())).willThrow(new IllegalStateException("일시 DB 오류"));

        new SampleCourseScheduler(generator, true).onStartup();   // 예외가 새 나오면 이 줄에서 터진다

        verify(generator).generate(내일);
    }
}
