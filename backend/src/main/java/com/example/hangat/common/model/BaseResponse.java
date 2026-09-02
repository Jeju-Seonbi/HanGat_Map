package com.example.hangat.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.example.hangat.common.model.BaseResponseStatus.SUCCESS;

/** 공통 응답 포맷 {success, code, message, result} (Nexus 컨벤션) */
@Getter
@AllArgsConstructor
public class BaseResponse<T> {
    private final Boolean success;
    private final Integer code;
    private final String message;
    private final T result;

    public static <T> BaseResponse<T> success(T result) {
        return new BaseResponse<>(SUCCESS.isSuccess(), SUCCESS.getCode(), SUCCESS.getMessage(), result);
    }

    public static <T> BaseResponse<T> fail(BaseResponseStatus status) {
        return new BaseResponse<>(status.isSuccess(), status.getCode(), status.getMessage(), null);
    }

    public static <T> BaseResponse<T> fail(BaseResponseStatus status, T result) {
        return new BaseResponse<>(status.isSuccess(), status.getCode(), status.getMessage(), result);
    }
}
