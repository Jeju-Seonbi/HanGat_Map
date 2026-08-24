package com.example.hangat.user.model;

import com.example.hangat.common.util.DateTimes;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 비밀번호 찾기 요청 모델 - 임시 비밀번호나 재설정 링크를 일회용으로 관리하기 위함 (USER_003).
 * 여기도 원문은 메일로만 보내고 DB에는 SHA-256 해시만 저장함.
 * 한 번 쓰면 used_at이 찍혀서 같은 링크를 다시 못 씀.
 */
@Table(
        name = "password_reset_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_prr_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_prr_user_used", columnList = "user_id, used_at"),
                @Index(name = "idx_prr_expires_at", columnList = "expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
@Entity
public class PasswordResetRequest {

    /** 명세에 수치가 없어서 정한 값. 계정 탈취로 바로 이어지는 경로라 인증 메일(24h)보다 짧게 잡음 */
    public static final Duration TTL = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 원문 토큰의 SHA-256 hex. 원문은 안 남김 */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** null이면 아직 안 쓴 요청 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 남용 감지·감사용. 보존 정책 필요 */
    @Setter
    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ────────────────────────── 발급 ──────────────────────────

    public static PasswordResetRequest issue(User user, String tokenHash) {
        return PasswordResetRequest.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(DateTimes.nowUtc().plus(TTL))
                .build();
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    /** 사용 처리. 비밀번호를 실제로 바꾸는 것과 같은 트랜잭션에서 불러야 함 */
    public void markUsed() {
        if (this.usedAt != null) {
            return; // 멱등
        }
        this.usedAt = DateTimes.nowUtc();
    }

    /** 재요청할 때 옛날 요청 죽이기 */
    public void invalidate() {
        markUsed();
    }

    // ────────────────────────── 조회 ──────────────────────────

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(DateTimes.nowUtc());
    }

    /** 재설정에 쓸 수 있는 요청인지 */
    public boolean isUsable() {
        return !isUsed() && !isExpired();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = DateTimes.nowUtc();
    }
}
