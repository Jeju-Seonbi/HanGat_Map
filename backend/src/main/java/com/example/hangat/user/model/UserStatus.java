package com.example.hangat.user.model;

import com.example.hangat.common.model.BaseResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 계정 상태 - 로그인 가능 여부를 여기서 판단함.
 * 막힌 이유(BaseResponseStatus)를 같이 들고 있어서 서비스에서 분기 안 해도 됨.
 */
@AllArgsConstructor
@Getter
public enum UserStatus {

    PENDING(false, "이메일 인증 대기", BaseResponseStatus.EMAIL_NOT_VERIFIED),
    ACTIVE(true, "정상", null),
    SUSPENDED(false, "이용제한", BaseResponseStatus.ACCOUNT_SUSPENDED),
    WITHDRAWN(false, "탈퇴", BaseResponseStatus.ACCOUNT_WITHDRAWN);

    private final boolean loginAllowed;
    private final String description;
    private final BaseResponseStatus loginDeniedStatus;
}
