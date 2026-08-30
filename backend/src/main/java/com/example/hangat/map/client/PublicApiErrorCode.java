package com.example.hangat.map.client;

import java.util.Arrays;

/**
 * 공공데이터포털 공통 에러코드 - 배치가 어떻게 대응해야 하는지에 따라 나눈다.
 *
 * <p>포털은 성공/실패를 두 겹으로 알려준다. HTTP 200에 서비스 헤더가 실패인 경우도 있고,
 * 아예 포털 레벨에서 {@code OpenAPI_ServiceResponse} 봉투로 돌아오는 경우도 있다.
 * 여기 코드는 후자(포털 레벨)의 {@code returnReasonCode}다.
 *
 * <p>★ {@link #QUOTA_EXCEEDED}(22)를 재시도하면 다음날 쿼터까지 태운다.
 * 개발계정이 API당 하루 1,000건뿐이라, 22를 받으면 즉시 중단하고 이전 캐시를 유지해야 한다(설계서 §7).
 */
public enum PublicApiErrorCode {

    /** 일일 트래픽 초과. <b>재시도 금지</b> - 배치를 중단하고 이전 데이터를 유지한다. */
    QUOTA_EXCEEDED("22", false),

    /** 초당 호출 제한. 잠시 쉬고 다시 시도해도 된다. */
    RATE_LIMITED("23", true),

    /** 등록되지 않은 키. 발급 직후 30분~1시간은 이 코드가 나므로 배포 직후라면 기다린다. */
    KEY_NOT_REGISTERED("30", false),

    /** 서비스 접근 거부 / 일시적 사용 중지 등. */
    SERVICE_ACCESS_DENIED("20", false),

    /** 필수 파라미터 누락 - 우리 코드 버그다. 재시도해도 똑같다. */
    MISSING_PARAMETER("11", false),

    /** 위에 없는 코드. */
    UNKNOWN(null, false);

    private final String code;
    private final boolean retryable;

    PublicApiErrorCode(String code, boolean retryable) {
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    /** 잠시 후 다시 시도해도 되는 오류인지. false면 즉시 중단한다. */
    public boolean isRetryable() {
        return retryable;
    }

    public static PublicApiErrorCode from(String returnReasonCode) {
        return Arrays.stream(values())
                .filter(e -> e.code != null && e.code.equals(returnReasonCode))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
