package com.example.hangat.user.model.oauth;

/**
 * 소셜 로그인 후 가입·계정 연결이 어느 단계까지 진행됐는지 나타냄.
 * 프론트는 이 값을 기준으로 이메일 입력, 코드 입력, 계정 연결 화면을 선택한다.
 */
public enum OAuthFlowStep {

    // 이메일, 닉네임 입력이 필요한 단계
    PROFILE_REQUIRED,

    // 기존 계정 연결 여부를 먼저 확인해야 하는 단계
    LINK_CONFIRMATION,

    // 이메일로 발송한 6자리 코드를 입력해야 하는 단계
    CODE_REQUIRED,

    // 이메일 인증은 끝났고 기존 계정 연결 동의만 남은 단계
    VERIFIED_LINK_CONFIRMATION,

    // 가입 또는 연결과 자동 로그인이 완료된 단계
    COMPLETED,

    // 사용자가 취소하여 더 이상 사용할 수 없는 단계
    CANCELLED
}
