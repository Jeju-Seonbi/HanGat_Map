package com.example.hangat.config.security;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthRateLimitExceptionHandler {

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<BaseResponse<Object>> handle() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(BaseResponse.fail(BaseResponseStatus.TOO_MANY_REQUESTS));
    }
}
