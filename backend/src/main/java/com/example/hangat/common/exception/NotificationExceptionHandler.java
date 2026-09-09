package com.example.hangat.common.exception;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.course.controller.AsyncCourseController;
import com.example.hangat.notification.controller.NotificationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 새 작업/알림 API의 HTTP 오류를 공통 응답으로 변환한다.
 * 기존 전역 예외 처리보다 먼저 해당 예외만 처리한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        AsyncCourseController.class,
        NotificationController.class
})
public class NotificationExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BaseResponse<Void>> handle(
            ResponseStatusException exception
    ) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new BaseResponse<>(
                        false,
                        exception.getStatusCode().value(),
                        exception.getReason() == null
                                ? "요청을 처리하지 못했습니다."
                                : exception.getReason(),
                        null
                ));
    }
}