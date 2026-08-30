package com.example.hangat.user.model.oauth;

import com.example.hangat.common.util.DateTimes;
import com.example.hangat.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 소셜 계정 연동 모델 - 한 회원이 카카오·구글을 같이 붙일 수 있게 하기 위함.
 * User에 provider 컬럼 하나만 두면 이메일 가입자가 나중에 구글을 붙일 때 덮어써야 해서 비번 로그인이 끊김.
 * 로그인할 때 providerUid로 찾아서 있으면 로그인, 없으면 가입시킴.
 */
@Table(
        name = "user_social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_usa_provider_uid",
                        columnNames = {"provider", "provider_uid"}),
                @UniqueConstraint(name = "uk_usa_user_provider",
                        columnNames = {"user_id", "provider"})
        },
        indexes = @Index(name = "idx_usa_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
@Entity
public class UserSocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 제공자가 준 고유 ID. 이메일은 사용자가 바꿀 수 있어서 키로 쓰면 계정을 잃음 */
    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    /** 연결 당시 확인된 이메일. 참고용이며 로그인 식별 기준은 아님 */
    @Setter
    @Column(length = 255)
    private String email;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ────────────────────────── 연동 ──────────────────────────

    public static UserSocialAccount link(User user, AuthProvider provider,
                                         String providerUid, String email) {
        return UserSocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerUid(providerUid)
                .email(email)
                .linkedAt(DateTimes.nowUtc())
                .build();
    }

    // ────────────────────────── 시간 관련 ──────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = DateTimes.nowUtc();
    }
}
