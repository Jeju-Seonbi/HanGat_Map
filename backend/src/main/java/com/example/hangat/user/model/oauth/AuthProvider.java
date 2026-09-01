package com.example.hangat.user.model.oauth;

/**
 * 소셜 로그인 제공자.
 * user_social_accounts에 행이 없으면 그냥 이메일 계정임.
 */
public enum AuthProvider {

    /** 이것은 카카오고 */
    KAKAO,

    /** 이것은 구글이여 */
    GOOGLE
}
