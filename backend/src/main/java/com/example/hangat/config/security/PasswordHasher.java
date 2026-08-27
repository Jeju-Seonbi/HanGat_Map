package com.example.hangat.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 *  비밀번호 해싱 진입점 - 비밀번호 정규화를 하도록 함.
 *  이유는 Mac은 NFD(분해형), 윈도우랑 안드로이드는 NFC(조합형)으로 각각 다르게 된다.
 *  해시의 경우 1바이트라도 달라지면 값이 달라지기 때문에 공평하게 정규화를 시킴.
 *  비밀번호 관련 서비스는 해당 클래스를 사용하도록 한다.
 */
@Component
@RequiredArgsConstructor
public class PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    // 새 비밀번호를 저장 가능한 해시로 바꿈.
    public String encodeNew(String password) {
        PasswordPolicy.validate(password);
        return passwordEncoder.encode(PasswordPolicy.normalize(password));
    }

    // 확인란까지 같이 받는 경우 (가입 / 재설정)
    public String encodeNew(String password, String passwordConfirm) {
        PasswordPolicy.validateConfirm(password, passwordConfirm);
        return encodeNew(password);
    }

    // 로그인 대조 - 정책 검사는 안함.
    public boolean matches(String password, String hash) {
        return passwordEncoder.matches(PasswordPolicy.normalize(password), hash);
    }
}
