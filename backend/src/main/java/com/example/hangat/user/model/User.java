package com.example.hangat.user.model;

import com.example.hangat.common.util.EmailNormalizer;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.time.LocalTime.now;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity(name = "users")
@AllArgsConstructor
@Builder
@Getter

/**
 * 회원
 *
 * 요구사항 : USER_001, USER_002, USER_003, MY_009, MY_010, MY_011
 *
 *
 */
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 255)
    private String email;

    @Setter
    private String password;

    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Setter
    @Column(nullable = false)
    private String nickname;

    @Setter
    private LocalDate birth_date;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    @Setter
    private LocalDateTime email_verified_at;

    @Setter
    private LocalDateTime password_changed_at;

    @Setter
    private LocalDateTime last_login_at;

    @Setter
    private LocalDateTime withdrawn_at;

    @Setter
    @Column(updatable = false)
    private LocalDateTime created_at;

    @Setter
    private LocalDateTime updated_at;

    // ────────────────────────── 가입 ──────────────────────────

    public static User signupWithEmail(String email, String encodedPassword,
                                             String nickname, LocalDate birth_Date) {
        return User.builder()
                .email(EmailNormalizer.normalize(email))
                .password(encodedPassword)
                .nickname(nickname)
                .birth_date(birth_Date)
                .status(UserStatus.PENDING)
                .build();
    }

    public static User signUpWithSocial(String email, String nickname, LocalDate birth_date) {
        return User.builder()
                .email(EmailNormalizer.normalize(email))
                .password(null)
                .nickname(nickname)
                .birth_date(birth_date)
                .status(UserStatus.ACTIVE)
                .email_verified_at(now())
                .build();
    }

    // ────────────────────────── 상태 변경 ──────────────────────────

    public void verifyEmail() {
        if(this.email_verified_at != null) {
            return;
        }
        this.email_verified_at = now();
        if(this.status == UserStatus.PENDING) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.password_changed_at = now();
    }

    public void TemporaryPassword(String temporaryPassword) {
        this.password = temporaryPassword;
        this.password_changed_at = now();
        this.mustChangePassword = true;
    }

    public void registerPassword(String encodedPassword) {
        changePassword(encodedPassword);
    }

    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            return; // 멱등
        }
        this.status = UserStatus.WITHDRAWN;
        this.withdrawn_at = now();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.created_at = now;
        this.updated_at = now;
        this.mustChangePassword = false;
    }

    @PreUpdate
    void onUpdate() {
        this.updated_at = LocalDateTime.now();
    }
}
