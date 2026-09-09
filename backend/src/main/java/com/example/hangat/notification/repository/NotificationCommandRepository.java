package com.example.hangat.notification.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 고유 키를 이용한 알림 중복 방지와 발송 신호 저장을 담당한다. */
@Repository
@RequiredArgsConstructor
public class NotificationCommandRepository {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    /** 호출 서비스의 트랜잭션에서 알림 본문과 발송 신호를 함께 저장한다. */
    public void insertWithOutbox(
            Long userId,
            String type,
            String title,
            String message,
            String targetType,
            String targetId,
            String dedupeKey
    ) {
        // 같은 트랜잭션의 JPA 변경이 JDBC 쓰기보다 먼저 DB에 반영되도록 한다.
        entityManager.flush();
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

        // 중복 요청이 먼저 저장한 행도 이전 읽기 스냅샷이 아닌 최신 상태에서 찾는다.
        Long notificationId = jdbc.queryForObject("""
                SELECT id
                FROM notifications
                WHERE user_id = ? AND dedupe_key = ?
                FOR UPDATE
                """,
                Long.class,
                userId,
                dedupeKey
        );

        jdbc.update("""
                INSERT INTO notification_outbox (
                    notification_id, user_id, created_at
                )
                VALUES (?, ?, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE notification_id = notification_id
                """,
                notificationId,
                userId
        );
    }
}
