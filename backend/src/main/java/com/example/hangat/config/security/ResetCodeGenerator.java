package com.example.hangat.config.security;

import java.security.SecureRandom;

/**
 * 비밀번호 찾을 때 메일에 보내는 숫자+영문자 코드 생성기
 */
public final class ResetCodeGenerator {

    /** 사용자가 헷갈릴만한 O랑 0, i랑1같은 애들은 제외함. */
    private static final String CODE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** 코드 자리수. DTO의 @Size(min=6, max=6)과 물려 있으니 같이 바꿔야 함 */
    public static final int CODE_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE.charAt(RANDOM.nextInt(CODE.length())));
        }
        return sb.toString();
    }

    /** 사용자가 소문자로 쳤거나 공백을 넣었을 수 있어서 대조 전에 정리함 */
    public static String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim()
                .replace(" ", "")
                .toUpperCase(java.util.Locale.ROOT);
    }
}
