package com.example.hangat.config.security.oauth;

import com.example.hangat.user.model.oauth.AuthProvider;

/**
 * Google·Kakao 사용자 정보를 한갓 내부 형식으로 변환한 값들.
 * providerUid만 소셜 계정 식별값으로 사용
 * Google 이메일은 가입·연결 판단에 사용하지만 로그인 식별값으로 사용하지 않는다.
 */
public record OAuthProviderUser (
    AuthProvider provider,
    String providerUid,
    String verifiedEmail
) {
}
