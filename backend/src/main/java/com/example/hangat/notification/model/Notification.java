package com.example.hangat.notification.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Notification {

    public record NotificationDto(
            String id,
            String type,
            String title,
            String message,
            String targetType,
            String targetId,
            Instant createdAt,
            Instant readAt
    ) {
    }

    public record NotificationPage(
            List<NotificationDto> items,
            String nextCursor,
            long unreadCount
    ) {
    }
}
