package com.example.hangat.config.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 토큰 생성 및 해싱 유틸
 * Bcrypt 대신 SHA-256을 쓰는 이유는 키 관리가 불필요해서 씀.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** URL이랑 쿠키에 넣을 수 있는 원문 토큰 */
    public static String generateToken() {
        // 보안과 관련있기때문에 넉넉하게 토큰 바이트를 32 바이트로 한다.
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** requestId 같은 짧은 식별자용*/
    public static String generateId() {
        // 얘는 16비트로 설정.
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** DB에 저장하는 Hash 값 */
    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 환경입니다.", e);
        }
    }

    /**
     * 해시 비교하는 메서드
     */
    public static boolean matchesHash(String rawInput, String storedHash) {
        return MessageDigest.isEqual(
                hash(rawInput).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
