package com.example.hangat.user.model;


/**
 * Refresh 토큰 폐기 사유
 *
 * Enum으로 상태들을 표기
 */
public enum RefreshRevokeReason {

    /** 사용자 로그 아웃 */
    LOGOUT,

    /** 이전 토큰 폐기 */
    ROTATED,

    /** 비밀번호 재설정시 전 기기 로그아웃 */
    PASSWORD_RESET,

    /** 관리자가 아무튼 막음 */
    SUSPENDED,

    /** 폐기된 토큰이 다시 들어왔다 => 탈취 의심 */
    REUSE_DETECTED;
}
