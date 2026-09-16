package com.example.hangat.common.storage.cleanup;

import com.example.hangat.common.storage.MinioFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import java.util.EnumMap;

@Slf4j
public class MediaCleanupJobService implements ApplicationRunner {
    private final MinioFileStorage storage;
    private final MediaCleanupService service;
    private final boolean dryRun;

    public MediaCleanupJobService(MinioFileStorage storage, MediaCleanupService service, boolean dryRun) {
        this.storage = storage;
        this.service = service;
        this.dryRun = dryRun;
    }

    @Override public void run(ApplicationArguments args) {
        var counts = new EnumMap<MediaCleanupService.Outcome, Integer>(MediaCleanupService.Outcome.class);
        int failures = 0;
        for (var image : storage.inventory()) {
            try {
                var result = service.inspect(image, dryRun);
                counts.merge(result, 1, Integer::sum);
                if (result == MediaCleanupService.Outcome.CANDIDATE
                        || result == MediaCleanupService.Outcome.WOULD_DELETE
                        || result == MediaCleanupService.Outcome.DELETED) {
                    log.info("사진 정리 대상 dryRun={} result={} key={}", dryRun, result, image.key());
                }
            } catch (RuntimeException e) {
                failures++;
                // SDK 오류 본문에는 내부 주소가 포함될 수 있으므로 출력하지 않는다.
                log.warn("사진 정리 실패 type={}", e.getClass().getSimpleName());
            }
        }
        log.info("사진 정리 결과 dryRun={} counts={} failures={}", dryRun, counts, failures);
        if (failures > 0) throw new IllegalStateException("사진 정리 실패 " + failures + "건: 다음 실행에서 재확인합니다.");
    }
}
