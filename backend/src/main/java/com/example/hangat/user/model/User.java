package com.example.hangat.user.model;

import com.example.hangat.common.util.DateTimes;
import com.example.hangat.common.util.EmailNormalizer;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 모델 - 로그인·가입·프로필의 기준 데이터.
 * 소셜 계정은 비밀번호가 없어서 password를 null 허용으로 뒀고, 연동 정보는 user_social_accounts가 따로 들고 있음.
 * USER_001~003, MY_009~011에서 씀.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")
        },
        indexes = {
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_last_login_at", columnList = "last_login_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID. EmailNormalizer 통과한 값만 넣음 */
    @Setter
    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt 해시만 저장. 소셜 전용 계정은 null */
    @Setter
    @Column(length = 255)
    private String password;

    /** 표시 이름. 실명 컬럼은 안 씀 */
    @Setter
    @Column(nullable = false, length = 50)
    private String nickname;

    /** 프로필 선택 정보 (MY_009) */
    @Setter
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    /** 인증 메일 누른 시각. null이면 미인증 (USER_002) */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** 재설정·직접 변경 완료 시각 */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /** 임시 비밀번호 받은 회원에게 변경을 강제함 (USER_003) */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** 로그인 성공 시 갱신 (USER_001) */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** status=WITHDRAWN일 때만 값이 있어야 함 (DB CHECK) */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ────────────────────────── 가입 ──────────────────────────

    /**
     * 이메일 가입 (USER_002).
     * 인증 메일 누르기 전까지 PENDING이라 로그인 안 됨.
     * encodedPassword는 BCrypt 돌린 값만 넘길 것.
     */
    public static User signUpWithEmail(String email, String encodedPassword,
                                       String nickname, LocalDate birthDate) {
        return User.builder()
                .email(EmailNormalizer.normalize(email))
                .password(encodedPassword)
                .nickname(nickname)
                .birthDate(birthDate)
                .status(UserStatus.PENDING)
                .build();
    }

    /**
     * 소셜 가입.
     * 제공자가 이메일을 이미 확인해줘서 PENDING 건너뛰고 바로 ACTIVE로 만듬.
     * 카카오는 미인증 이메일을 줄 수 있으니 그때는 이거 쓰면 안 됨.
     */
    public static User signUpWithSocial(String email, String nickname, LocalDate birthDate) {
        return User.builder()
                .email(EmailNormalizer.normalize(email))
                .password(null)
                .nickname(nickname)
                .birthDate(birthDate)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(DateTimes.nowUtc())
                .build();
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    /**
     * 인증 메일 클릭 처리 (USER_002).
     * PENDING일 때만 ACTIVE로 올림 - 정지된 회원이 링크 눌러서 풀리면 안 되니까.
     */
    public void verifyEmail() {
        if (this.emailVerifiedAt != null) {
            return; // 이미 인증됨. 링크를 두 번 눌러도 오류 없이 넘어간다
        }
        this.emailVerifiedAt = DateTimes.nowUtc();
        if (this.status == UserStatus.PENDING) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /** 비밀번호 변경. 이미 인코딩된 값을 받음 */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedAt = DateTimes.nowUtc();
        this.mustChangePassword = false;
    }

    /** 임시 비밀번호 발급. 다음 로그인 때 변경 강제 (USER_003) */
    public void issueTemporaryPassword(String encodedTemporaryPassword) {
        this.password = encodedTemporaryPassword;
        this.passwordChangedAt = DateTimes.nowUtc();
        this.mustChangePassword = true;
    }

    /** 소셜 전용 계정이 비밀번호를 처음 설정하는 경우 */
    public void registerPassword(String encodedPassword) {
        changePassword(encodedPassword);
    }

    /** 로그인 성공 시 갱신 (USER_001) */
    public void recordLogin() {
        this.lastLoginAt = DateTimes.nowUtc();
    }

    /**
     * 탈퇴.
     * status와 withdrawnAt이 같이 움직여야 DB CHECK를 안 어김.
     */
    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            return; // 멱등
        }
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = DateTimes.nowUtc();
    }

    // ────────────────────────── 조회 ──────────────────────────

    /** 미인증·이용제한·탈퇴는 로그인 못 함 (USER_001) */
    public boolean canLogin() {
        return this.status.isLoginAllowed();
    }

    /** 소셜 전용 계정 여부. 비번 로그인 시도 구분용 */
    public boolean hasPassword() {
        return this.password != null;
    }

    /** 이메일 인증 완료 여부 (USER_002) */
    public boolean isEmailVerified() {
        return this.emailVerifiedAt != null;
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

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

    // ────────────────────────── 기타 ──────────────────────────

    /**
     * password는 절대 안 넣음.
     * @ToString이나 @Data 붙이면 비밀번호 해시가 로그에 그대로 찍힘.
     */
    @Override
    public String toString() {
        return "User(id=" + id + ", email=" + email + ", nickname=" + nickname
                + ", status=" + status + ")";
    }
}
