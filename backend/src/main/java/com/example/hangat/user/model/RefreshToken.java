package com.example.hangat.user.model;


import com.example.hangat.common.util.DateTimes;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Refresh 토큰 모델 - JWT 토큰이 탈취를 감지하기 위함.
 * httpOnly만 되게끔 하고 보안에 좀 더 튼튼하게 하기 위함.
 * 한 곳에서 사용중일 때 다른 곳에서도 쓰는걸 감지하면 refresh토큰을 다시 만듬.
 */

@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rt_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_rt_user_revoked_expires",
                            columnList = "user_id, revoked_at, expires_at"),
                @Index(name = "idx_rt_expires_at", columnList = "expires_at"),
                @Index(name = "idx_rt_revoked_at", columnList = "revoked_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
@Entity
public class RefreshToken {

    public static final Duration Absolute_TTL = Duration.ofDays(14);

    public static final Duration IDLE_TTL = Duration.ofHours(12);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Setter
    @Column(name = "device_label", length = 100)
    private String deviceLabel;

    /** 최근 로그인 기기 식별 보조 */
    @Setter
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Setter
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 회전·사용 시 갱신. 유휴 만료 판정 기준이 된다 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 명세서 CHECK(revoked_at IS NULL OR revoked_at >= created_at) */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private RefreshRevokeReason revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ────────────────────────── 발급 ──────────────────────────

    public static RefreshToken issue(User user, String tokenHash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(DateTimes.nowUtc().plus(Absolute_TTL))
                .build();
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    /**
     * 폐기. revokedAt과 revokedReason을 같이 세팅함.
     * 이미 폐기된 토큰은 최초 사유를 유지 - 덮으면 탈취 흔적이 지워짐.
     */
    public void revoke(RefreshRevokeReason reason) {
        if (this.revokedAt != null) {
            return; // 멱등
        }
        this.revokedAt = DateTimes.nowUtc();
        this.revokedReason = reason;
    }

    /** 사용 시각 기록. 만료 판정엔 안 쓰고 감사용 */
    public void touch() {
        this.lastUsedAt = DateTimes.nowUtc();
    }

    // ────────────────────────── 조회 ──────────────────────────

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    /** 절대 만료. 계속 써도 이 시점엔 끊김 */
    public boolean isExpired() {
        return this.expiresAt.isBefore(DateTimes.nowUtc());
    }

    /**
     * 유휴 만료. 회전하면 새 행이 생기니 createdAt이 곧 마지막 재발급 시각임.
     */
    public boolean isIdleExpired() {
        LocalDateTime base = (this.lastUsedAt != null) ? this.lastUsedAt : this.createdAt;
        return base.plus(IDLE_TTL).isBefore(DateTimes.nowUtc());
    }

    /** 재발급에 쓸 수 있는 토큰인지 */
    public boolean isUsable() {
        return !isRevoked() && !isExpired() && !isIdleExpired();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = DateTimes.nowUtc();
    }
}
