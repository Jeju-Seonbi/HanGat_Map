package com.example.hangat.user.repository;

import com.example.hangat.user.model.oauth.AuthProvider;
import com.example.hangat.user.model.oauth.UserSocialAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 *  구글의 경우 sub를 카카오의 경우 id로 기존 계정과의 연결 정보를 조회함.
 *  이메일은 변경될 수 있으므로 소셜 로그인의 식별 조건으로 사용하진 않음.
 */
public interface UserSocialAccountRepository
        extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderUid(
            AuthProvider provider, String providerUid
    );

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);
}
