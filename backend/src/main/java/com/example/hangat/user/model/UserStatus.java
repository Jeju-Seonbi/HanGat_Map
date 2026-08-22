package com.example.hangat.user.model;

import com.example.hangat.common.model.BaseResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

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
