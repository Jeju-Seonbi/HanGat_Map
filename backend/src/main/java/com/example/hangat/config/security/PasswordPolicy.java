package com.example.hangat.config.security;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/**
 *  비밀번호 정책 - 가입 / 재설정 / 변경 때만 검사
 */
public final class PasswordPolicy {

    /** 비밀번호 최소 자리 */
    public static final int MIN_LENGTH = 12;
    /** BCrypt 알고리즘 사용 알고리즘 구조상 최대 숫자인 72를 사용 */
    public static final int MAX_BYTES = 72;


    public static String normalize(String password) {
        if(password == null) return null;
        else return Normalizer.normalize(password, Normalizer.Form.NFC);
    }

    /** 새 비밀번호 검사 (로그인 경계에서는 안씀) */
    public static void validate(String password) {
        String newPassword = normalize(password);

        // 새 비밀번호가 없거나 짧을 경우
        if(newPassword == null ||
                newPassword.codePointCount(0, newPassword.length()) < MIN_LENGTH) {
            throw new BaseException(BaseResponseStatus.PASSWORD_TOO_SHORT);
        }
        // 비밀번호가 BCrypt보다 클 경우
        if(newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BaseException(BaseResponseStatus.PASSWORD_TOO_LONG);
        }
    }

    /** 비밀번호 확인란이랑 일치하는지도 확인 */
    public static void validateConfirm(String password, String passwordConfirm) {
        if(!Objects.equals(normalize(password), normalize(passwordConfirm))) {
            throw new BaseException(BaseResponseStatus.PASSWORD_CONFIRM_MISMATCH);
        }
    }
}
