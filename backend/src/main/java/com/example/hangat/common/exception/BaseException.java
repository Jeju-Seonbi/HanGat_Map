package com.example.hangat.common.exception;

import com.example.hangat.common.model.BaseResponseStatus;
import lombok.Getter;

/** 도메인 공통 예외 (Nexus 컨벤션) */
@Getter
public class BaseException extends RuntimeException {
    private final BaseResponseStatus status;
    /** 실패 응답의 {@code result}에 실을 추가 정보 */
    private final Object result;

    public BaseException(BaseResponseStatus status) {
        this(status, null);
    }

    public BaseException(BaseResponseStatus status, Object result) {
        super(status.getMessage());
        this.status = status;
        this.result = result;
    }

    public static BaseException from(BaseResponseStatus status) {
        return new BaseException(status);
    }
}
