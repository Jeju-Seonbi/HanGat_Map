package com.example.hangat.config.security.token;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 사용자가 입력하는 짧은 인증코드의 생성과 형식을 관리
 * 해싱이나 DB저장은 담당하지 않음.
 */
@Component
public final class VerificationCodeGenerator {

    /** 사용자가 헷갈릴만한 O랑 0, i랑1같은 애들은 제외함. */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final String INPUT_PATTERN =
            "^[2-9A-HJ-NP-Za-hj-np-z]{6}$";

    /** 공백 제거와 대문자 정규화가 끝난 인증 코드 자리수 */
    public static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    // 같은 패키지에 있는 VerificationCodeHasher에서 사용
    String normalize(String raw) {
        if(raw == null) {
            return null;
        }

        return raw.trim()
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    // normalize()가 끝난 값을 검사
    boolean isValid(String normalized) {
        if(normalized == null || normalized.length() != CODE_LENGTH) {
            return false;
        }

        return normalized.chars()
                .allMatch(code -> ALPHABET.indexOf(code) >= 0);
    }
}
