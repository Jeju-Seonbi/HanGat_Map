package com.example.hangat.notification.service;

import com.example.hangat.notification.model.Notification.NotificationDto;
import com.example.hangat.notification.model.Notification.NotificationPage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 알림 저장 / 조회 / 읽음 처리.
 *
 * 알림 본문은 DB에 보관한다.
 * SSE 전송 실패가 알림 유실로 이어지지 않도록 전송 신호도 함께 저장한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbc;

    @Value("${hangat.notifications.enabled:false}")
    private boolean enabled;

    // ────────────────────────── 알림 생성 ──────────────────────────

    /**
     * 같은 사용자와 dedupeKey에는 알림을 한 번만 만든다.
     * 호출자의 트랜잭션이 있으면 그 트랜잭션에 참여한다.
     */
    @Transactional
    public void enqueue(
            Long userId,
            String type,
            String title,
            String message,
            String targetType,
            String targetId,
            String dedupeKey
    ) {
        // 로컬/배치에서 알림을 끈 경우 새 알림 테이블 없이 기존 인증 기능을 사용한다.
        if (!enabled) return;
        jdbc.update("""
                INSERT INTO notifications (
                    user_id, type, title, message,
                    target_type, target_id, dedupe_key, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE id = id
                """,
                userId, type, title, message,
                targetType, targetId, dedupeKey
        );

        Long notificationId = jdbc.queryForObject("""
                SELECT id
                FROM notifications
                WHERE user_id = ? AND dedupe_key = ?
                """,
                Long.class, userId, dedupeKey
        );

        jdbc.update("""
                INSERT INTO notification_outbox (
                    notification_id, user_id, created_at
                )
                VALUES (?, ?, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE notification_id = notification_id
                """,
                notificationId, userId
        );
    }

    // ────────────────────────── 알림 조회 ──────────────────────────

    public NotificationPage list(Long userId, Long cursor, int requestedSize) {
        int size = Math.max(1, Math.min(50, requestedSize));
        long before = cursor == null ? Long.MAX_VALUE : cursor;

        List<NotificationDto> rows = jdbc.query("""
                SELECT id, type, title, message, target_type, target_id,
                       created_at, read_at
                FROM notifications
                WHERE user_id = ? AND id < ?
                ORDER BY id DESC
                LIMIT ?
                """,
                this::mapNotification,
                userId, before, size + 1
        );

        boolean hasNext = rows.size() > size;
        List<NotificationDto> items = hasNext
                ? List.copyOf(rows.subList(0, size))
                : List.copyOf(rows);

        String nextCursor = hasNext
                ? items.get(items.size() - 1).id()
                : null;

        Long unread = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ? AND read_at IS NULL
                """,
                Long.class, userId
        );

        return new NotificationPage(
                items,
                nextCursor,
                unread == null ? 0 : unread
        );
    }

    // ────────────────────────── 읽음 처리 ──────────────────────────

    @Transactional
    public void read(Long userId, Long notificationId) {
        jdbc.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, UTC_TIMESTAMP(6))
                WHERE id = ? AND user_id = ?
                """,
                notificationId, userId
        );
    }

    @Transactional
    public void readAll(Long userId) {
        jdbc.update("""
                UPDATE notifications
                SET read_at = UTC_TIMESTAMP(6)
                WHERE user_id = ? AND read_at IS NULL
                """,
                userId
        );
    }

    private NotificationDto mapNotification(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new NotificationDto(
                rs.getString("id"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                instant(rs, "created_at"),
                instant(rs, "read_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
