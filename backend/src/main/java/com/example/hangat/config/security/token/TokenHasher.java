package com.example.hangat.config.security.token;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 서버가 생성한 고엔트로피 토큰 및 식별자용 유틸.
 * 비밀번호와 짧은 인증 코드에는 사용하지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int ID_BYTES = 16;

    // ────────────────────────── 안전한 난수 생성 ──────────────────────────

    /** URL이랑 쿠키에 넣을 수 있는 256비트 원문 토큰 */
    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /** requestId 같은 128비트 식별자용 */
    public static String generateId() {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // ────────────────────────── DB 저장용 SHA-256 ──────────────────────────

    public static String hash(String raw) {
        return HexFormat.of().formatHex(sha256(raw));
    }

    private static byte[] sha256(String raw) {
        Objects.requireNonNull(raw, "raw");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return digest.digest(
                    raw.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256을 지원하지 않는 환경입니다.",
                    e);
        }
    }
}
