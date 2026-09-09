package com.example.hangat.notification.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.notification.NotificationStream;
import com.example.hangat.notification.model.Notification;
import com.example.hangat.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 내 알림 조회 / 읽음 처리 / 실시간 연결 API.
 * 모든 경로는 로그인한 본인의 정보만 다룬다.
 */
@RestController
@RequestMapping("/users/me/notifications")
@Profile("!batch")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "hangat.notifications.enabled",
        havingValue = "true"
)
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationStream stream;

    @GetMapping
    public ResponseEntity<BaseResponse<Notification.NotificationPage>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(BaseResponse.success(
                        notifications.list(userId, cursor, size)
                ));
    }

    @PutMapping("/{id}/read")
    public BaseResponse<Void> read(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notifications.read(userId, id);
        return BaseResponse.success(null);
    }

    @PutMapping("/read-all")
    public BaseResponse<Void> readAll(
            @AuthenticationPrincipal Long userId
    ) {
        notifications.readAll(userId);
        return BaseResponse.success(null);
    }

    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> connect(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Accel-Buffering", "no")
                .body(stream.connect(userId));
    }
}
