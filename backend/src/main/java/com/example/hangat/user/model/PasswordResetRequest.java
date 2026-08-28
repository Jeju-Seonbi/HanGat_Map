package com.example.hangat.user.model;

import com.example.hangat.common.util.DateTimes;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 비밀번호 찾기 요청 모델 - 6자리 코드 3단계를 한 행으로 관리하기 위함 (USER_003).
 * 1단계 코드 발송 -> 2단계 코드 확인 후 티켓 발급 -> 3단계 티켓으로 새 비밀번호 설정.
 * 코드도 티켓도 원문은 메일로만 보내고 DB에는 SHA-256 해시만 저장함.
 */
@Table(
        name = "password_reset_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_prr_request_id", columnNames = "request_id"),
                @UniqueConstraint(name = "uk_prr_ticket_hash", columnNames = "ticket_hash")
        },
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

    /** 코드 수명. 프론트 RESET_CODE_TTL_MS와 맞춤 */
    public static final Duration CODE_TTL = Duration.ofMinutes(10);

    /** 티켓 수명. 코드 확인 후 비밀번호를 입력할 시간 */
    public static final Duration TICKET_TTL = Duration.ofMinutes(5);

    /** 이 횟수만큼 틀리면 코드를 폐기함. 6자리는 엔트로피가 낮아서 이게 필수 */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 1단계에서 발급하는 요청 식별자.
     * 2단계 조회에 사용하는 식별자다. 인증 코드는 조회 조건으로 사용하지 않는다.
     * 이게 없으면 남이 요청해둔 코드에 아무나 무차별 대입할 수 있음.
     */
    @Column(name = "request_id", nullable = false, length = 32)
    private String requestId;

    /** 6자리 코드의 SHA-256 해시. 원문은 이메일로만 전달한다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 코드를 틀린 횟수. MAX_ATTEMPTS 되면 이 요청은 죽음 */
    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /** 2단계 통과 시각. null이면 아직 코드 확인 안 한 요청 */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** 2단계 통과 후 발급한 티켓의 SHA-256 hex */
    @Column(name = "ticket_hash", length = 64)
    private String ticketHash;

    /** 티켓 만료 시각. 코드 만료와 별개로 둠 */
    @Column(name = "ticket_expires_at")
    private LocalDateTime ticketExpiresAt;

    /** null이면 아직 안 쓴 요청 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 남용 감지·감사용. 보존 정책 필요 */
    @Setter
    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ────────────────────────── 1단계 코드 발급 ──────────────────────────

    public static PasswordResetRequest issue(User user, String requestId, String codeHash) {
        return PasswordResetRequest.builder()
                .user(user)
                .requestId(requestId)
                .tokenHash(codeHash)
                .expiresAt(DateTimes.nowUtc().plus(CODE_TTL))
                .build();
    }

    /** 재요청 시 이전 요청을 무효화한다. */
    public void invalidate() {
        markUsed();
    }

    // ────────────────────────── 2단계 코드 확인 ──────────────────────────

    /** 코드가 틀렸을 때. MAX_ATTEMPTS에 닿으면 isCodeUsable()이 false가 됨 */
    public void addFailedAttempt() {
        this.attemptCount++;
    }

    /** 코드가 맞았을 때. 티켓을 발급하고 코드는 더 못 쓰게 됨 */
    public void markVerified(String ticketHash) {
        this.verifiedAt = DateTimes.nowUtc();
        this.ticketHash = ticketHash;
        this.ticketExpiresAt = DateTimes.nowUtc().plus(TICKET_TTL);
    }

    // ────────────────────────── 3단계 : 비밀번호 변경 ──────────────────────────

    /** 실제로 비밀번호를 바꾸는 것과 같은 트랜잭션에서 불러야 함 */
    public void markUsed() {
        if (this.usedAt != null) {
            return; // 멱등
        }
        this.usedAt = DateTimes.nowUtc();
    }

    // ────────────────────────── 조회 ──────────────────────────

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public boolean isVerified() {
        return this.verifiedAt != null;
    }

    public boolean isAttemptsExceeded() {
        return this.attemptCount >= MAX_ATTEMPTS;
    }

    public boolean isCodeExpired() {
        return this.expiresAt.isBefore(DateTimes.nowUtc());
    }

    /** 2단계에서 코드를 대조해도 되는 요청인지 */
    public boolean isCodeUsable() {
        return !isUsed() && !isVerified() && !isCodeExpired() && !isAttemptsExceeded();
    }

    public boolean isTicketExpired() {
        return this.ticketExpiresAt == null || this.ticketExpiresAt.isBefore(DateTimes.nowUtc());
    }

    /** 3단계에서 비밀번호를 바꿔도 되는 요청인지 */
    public boolean isTicketUsable() {
        return isVerified() && !isUsed() && !isTicketExpired();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = DateTimes.nowUtc();
    }
}
