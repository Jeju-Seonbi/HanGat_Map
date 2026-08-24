package com.example.hangat.user.model;

import com.example.hangat.common.util.DateTimes;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이메일 인증 토큰 모델 - 가입한 이메일이 진짜 본인 것인지 확인하기 위함 (USER_002).
 * 메일 링크에는 원문을 담고 DB에는 SHA-256 해시만 저장해서, DB가 털려도 링크를 못 만들게 함.
 * 링크를 누르면 이 토큰을 소진하고 User를 PENDING에서 ACTIVE로 올림.
 */
@Table(
        name = "email_verification_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evt_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_evt_user_used", columnList = "user_id, used_at"),
                @Index(name = "idx_evt_expires_at", columnList = "expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
@Entity
public class EmailVerificationToken {

    /** 인증 링크 유효 시간 */
    public static final Duration TTL = Duration.ofHours(24);

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

    /** null이면 아직 안 쓴 토큰 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ────────────────────────── 발급 ──────────────────────────

    public static EmailVerificationToken issue(User user, String tokenHash) {
        return EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(DateTimes.nowUtc().plus(TTL))
                .build();
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    /** 사용 처리. User.verifyEmail()과 같은 트랜잭션에서 불러야 함 */
    public void markUsed() {
        if (this.usedAt != null) {
            return; // 멱등
        }
        this.usedAt = DateTimes.nowUtc();
    }

    /** 재발송할 때 옛날 토큰 죽이기. DB가 못 막아주니 코드로 해야 함 */
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

    /** 인증에 쓸 수 있는 토큰인지 */
    public boolean isUsable() {
        return !isUsed() && !isExpired();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = DateTimes.nowUtc();
    }
}
