package com.example.hangat.batch;

import com.example.hangat.course.service.SampleCourseGenerator;
import com.example.hangat.domain.weather.TripWeatherIngestService;
import com.example.hangat.domain.weather.WeatherIngestService;
import com.example.hangat.map.congestion.CongestionIngestService;
import com.example.hangat.notification.service.trip.TripNotificationJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Kubernetes Job용 단발성 실행기 - batch 프로필에서 지정한 작업만 한 번 실행한다.
 * 예외와 불완전한 적재 결과를 기동 실패로 전달해 실패한 Job이 성공으로 기록되지 않게 한다.
 */
@Slf4j
@Component
@Profile("batch")
public class BatchJobRunner implements ApplicationRunner {
    private final String job;
    private final CongestionIngestService congestion;
    private final WeatherIngestService weather;
    private final SampleCourseGenerator courses;
    private final BatchPrerequisiteChecker prerequisites;
    private final TripWeatherIngestService tripWeather;
    private final TripNotificationJobService tripNotifications;

    public BatchJobRunner(@Value("${hangat.batch.job:}") String job,
                          CongestionIngestService congestion, WeatherIngestService weather,
                          SampleCourseGenerator courses, BatchPrerequisiteChecker prerequisites,
                          TripWeatherIngestService tripWeather, TripNotificationJobService tripNotifications) {
        this.job = job;
        this.congestion = congestion;
        this.weather = weather;
        this.courses = courses;
        this.prerequisites = prerequisites;
        this.tripWeather = tripWeather;
        this.tripNotifications = tripNotifications;
    }

    /** 기존 스케줄러의 예외 흡수·재시도는 사용하지 않는다. 재시도 횟수는 Job이 관리한다. */
    @Override
    public void run(ApplicationArguments args) {
        log.info("배치 시작 job={}", job);
        switch (job) {
            case "congestion" -> {
                var result = congestion.ingest();
                if (result.saved() == 0) {
                    throw new IllegalStateException("혼잡도 적재 실패: 저장된 예보가 없습니다.");
                }
                tripNotifications.run("congestion");

                log.info("혼잡도 배치 결과 {}", result);
            }
            case "weather" -> {
                var result = weather.ingest();

                int notificationWeatherFailures = tripWeather.ingest();

                // 성공적으로 저장된 자료를 비교한다.
                // 실패로 누락된 값은 FactsService가 '맑음' 등으로 대체하지 않는다.
                tripNotifications.run("weather");

                if (!result.hasCompleteShortTermCoverage()
                        || result.midRows() == 0
                        || result.midFailed()
                        || notificationWeatherFailures > 0) {
                    throw new IllegalStateException(
                            "날씨 적재 불완전: " + result
                                    + ", 알림용 실패 권역=" + notificationWeatherFailures
                    );
                }

                log.info("날씨 배치 결과 {}", result);
            }
            case "sample-courses" -> {
                var startDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
                prerequisites.check(startDate);
                var result = courses.generate(startDate);
                if (!result.isComplete()) {
                    throw new IllegalStateException("샘플 코스 생성 불완전: " + result);
                }
                log.info("샘플 코스 배치 결과 {}", result);
            }
            case "trip-reminders" -> tripNotifications.run("reminders");

            default -> throw new IllegalArgumentException(
                    "hangat.batch.job은 congestion, weather, sample-courses, "
                            + "trip-reminders 중 하나여야 합니다."
            );
        }
        log.info("배치 완료 job={}", job);
    }
}
