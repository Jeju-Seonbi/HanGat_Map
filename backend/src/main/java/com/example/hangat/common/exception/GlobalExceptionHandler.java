package com.example.hangat.common.exception;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/** 공통 예외 → BaseResponse 변환 (Nexus 컨벤션) */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 → 필드별 메시지를 result에 담아 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.fail(BaseResponseStatus.REQUEST_ERROR, errors));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<Object>> handleBaseException(BaseException e) {
        BaseResponseStatus status = e.getStatus();
        BaseResponse<Object> response = e.getResult() != null
                ? BaseResponse.fail(status, e.getResult())
                : BaseResponse.fail(status);
        return ResponseEntity.status(httpStatusOf(status.getCode())).body(response);
    }

    /** 3000번대 → 400, 5000번대 → 500 */
    private int httpStatusOf(int errorCode) {
        return errorCode >= 5000 ? 500 : 400;
    }
}
