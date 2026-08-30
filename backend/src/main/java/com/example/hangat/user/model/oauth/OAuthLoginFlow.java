package com.example.hangat.user.model.oauth;

import com.example.hangat.common.util.DateTimes;
import com.example.hangat.config.security.token.Purpose;
import com.example.hangat.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 소셜 로그인, 가입, 연결 진행 상태를 저장한다.
 * OAuth provider access token은 저장하지 않는다.
 * Google:
 * - providerEmail에 Google이 검증한 이메일을 저장한다.
 *
 * Kakao:
 * - 공급자 이메일을 사용할 수 없으므로 providerEmail은 null이다.
 * - 사용자가 입력하고 인증한 이메일은 targetEmail에 저장한다.
 */

@Entity
@Table(
        name = "oauth_login_flows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_olf_flow_token_hash",
                        columnNames = "flow_token_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_olf_provider_uid",
                        columnList = "provider, provider_uid"
                ),
                @Index(
                        name = "idx_olf_expires_at",
                        columnList = "expires_at"
                ),
                @Index(
                        name = "idx_olf_consumed_at",
                        columnList = "consumed_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class OAuthLoginFlow {

    // ────────────────────────── 만료·시도 정책 ──────────────────────────

    // OAuth 가입 및 연결 전체 진행 시간
    public static final Duration FLOW_TTL = Duration.ofMinutes(15);
    // 이메일 인증코드 유효 시간
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    // 이메일 인증코드 최대 실패횟수
    public static final int MAX_TRY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 브라우저 쿠키에 담긴 진행중인 토큰의 해시
    @Column(name = "flow_token_hash", nullable = false, length = 64)
    private String flowTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    // 구글의 sub 또는 카카오 id
    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    // 구글의 경우 email을 받을 수 있지만 카카오는 사업자를 내야함.
    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    // 인증 코드를 발송한 이메일
    @Column(name = "target_email", length = 255)
    private String targetEmail;

    // 신규 가입에 사용할 닉네임
    @Column(length = 50)
    private String nickname;

    // 기존 계정에 연결할 때 대상이 되는 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OAuthFlowStep step;

    // 코드 사용 목적 -> HMAC 문맥 분리에 사용.
    @Enumerated(EnumType.STRING)
    @Column(name = "code_purpose", length = 30)
    private Purpose codePurpose;

    // 6자리 코드의 HMAC-SHA-256
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "code_expires_at")
    private LocalDateTime codeExpiresAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ────────────────────────── 진행 흐름 생성 ──────────────────────────

    /**
     *  처음 로그인한 구글 계정의 진행상태를 만듬.
     *
     *  구글 이메일이 기존 계정의 이메일과 일치하면 연결 확인부터 하고
     *  없을 경우 닉네임 입력부터 시작함.
     */
    public static OAuthLoginFlow startGoogle(
            String flowTokenHash,
            String providerUid,
            String providerEmail,
            User existingUser) {

        OAuthFlowStep initialStep = existingUser == null
                ? OAuthFlowStep.PROFILE_REQUIRED
                : OAuthFlowStep.LINK_CONFIRMATION;

        return OAuthLoginFlow.builder()
                .flowTokenHash(flowTokenHash)
                .provider(AuthProvider.GOOGLE)
                .providerUid(providerUid)
                .providerEmail(providerEmail)
                .targetEmail(existingUser == null
                        ? null
                        : existingUser.getEmail())
                .targetUser(existingUser)
                .step(initialStep)
                .expiresAt(DateTimes.nowUtc().plus(FLOW_TTL))
                .build();
    }

    /**
     *  처음 로그인한 카카오 계정의 진행 상태를 만든다.
     *  카카오의 경우 이메일을 받을려면 사업자를 내야하기때문에 별도의 페이지로 이메일, 닉네임을 설정함.
     */
    public static OAuthLoginFlow startKakao(
            String flowTokenHash,
            String providerUid) {

        return OAuthLoginFlow.builder()
                .flowTokenHash(flowTokenHash)
                .provider(AuthProvider.KAKAO)
                .providerUid(providerUid)
                .providerEmail(null)
                .step(OAuthFlowStep.PROFILE_REQUIRED)
                .expiresAt(DateTimes.nowUtc().plus(FLOW_TTL))
                .build();
    }

    // ────────────────────────── 인증 코드 발급 ──────────────────────────

    /**
     * 신규 가입에 사용할 이메일·닉네임과 인증 코드를 저장한다.
     */
    public void issueSignupCode(
            String targetEmail,
            String nickname,
            String codeHash) {

        this.targetEmail = targetEmail;
        this.nickname = nickname;
        this.codePurpose = Purpose.OAUTH_SIGNUP;
        issueCode(codeHash);
    }

    // 기존 계정 연결용 이메일 코드를 발급한 상태로 변경한다.
    public void issueLinkCode(String codeHash) {
        this.codePurpose = Purpose.OAUTH_LINK;
        issueCode(codeHash);
    }

    private void issueCode(String codeHash) {
        this.codeHash = codeHash;
        this.codeExpiresAt = DateTimes.nowUtc().plus(CODE_TTL);
        this.attemptCount = 0;
        this.emailVerifiedAt = null;
        this.step = OAuthFlowStep.CODE_REQUIRED;
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    // 인증코드 잘못 치면 실패 횟수 올림.
    public void addFailedAttempt() {
        this.attemptCount++;
    }
    // 이메일 코드 인증성공하면 기록.
    public void markEmailVerified() {
        this.emailVerifiedAt = DateTimes.nowUtc();
    }

    /**
     *  만약 이메일 인증 후 기존 계정이 있을 경우 연결할것인지 물어보기.
     *  카카오의 경우 이메일 자체를 못받기 때문에 이 메서드가 호출되기전까진 모름.
     */
    public void markVerifiedLinkConfirmation(User existingUser) {
        this.targetUser = existingUser;
        this.targetEmail = existingUser.getEmail();
        this.step = OAuthFlowStep.VERIFIED_LINK_CONFIRMATION;
    }

    /**
     *  구글 신규 가입을 준비하는 동안 동일 이메일 계정이 생긴경우
     *  연결할 것인지 확인.
     */
    public void prepareLinkConfirmation(User existingUser) {
        this.targetUser = existingUser;
        this.targetEmail = existingUser.getEmail();
        this.step = OAuthFlowStep.LINK_CONFIRMATION;
    }

    // 사용자가 가입 또는 연결을 거부하면 취소 처리하기
    public void cancel() {
        if(this.consumedAt != null) {
            return;
        }

        this.step = OAuthFlowStep.CANCELLED;
        markConsumed();
    }

    /**
     * 가입 또는 연결과 자동 로그인이 완료된 흐름을 재사용할 수 없게 만든다.
     */
    public void complete() {
        this.step = OAuthFlowStep.COMPLETED;
        markConsumed();
    }

    private void markConsumed() {
        if(this.consumedAt == null) {
            this.consumedAt = DateTimes.nowUtc();
        }
    }

    // ────────────────────────── 상태 조회 ──────────────────────────

    // 전체 OAuth 흐름이 아직 사용 가능한지 확인한다.
    public boolean isUsable() {
        return consumedAt == null
                && expiresAt.isAfter(DateTimes.nowUtc());
    }

    // 현재 이메일 코드를 검증할 수 있는 상태인지 확인.
    public boolean isCodeUsable() {
        return isUsable()
                && step == OAuthFlowStep.CODE_REQUIRED
                && codeHash != null
                && codeExpiresAt != null
                && codeExpiresAt.isAfter(DateTimes.nowUtc())
                && attemptCount < MAX_TRY
                && emailVerifiedAt == null;
    }

    // 이메일 코드 인증이 완료됐는지 확인.
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    // ────────────────────────── 시간 기록 ──────────────────────────

    @PrePersist
    void onCreate() {
        LocalDateTime now = DateTimes.nowUtc();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = DateTimes.nowUtc();
    }
}
