package com.example.hangat.config.security.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 짧은 인증 코드를 HMAC SHA-256으로 저장하고 비교함.
 * 단순 SHA-256과 달리 서버 비밀키가 없으면
 * DB 해시만으로 인증코드를 대입하게 어렵게 함.
 */
@Component
public final class VerificationCodeHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final VerificationCodeGenerator codeGenerator;
    private final SecretKeySpec hmacKey;

    public VerificationCodeHasher(
            VerificationCodeGenerator codeGenerator,
            @Value("${app.auth.code-hmac-secret}")
            String base64Secret) {

        this.codeGenerator = codeGenerator;
        this.hmacKey = createKey(base64Secret);
    }

    // 새 코드를 DB 저장용으로 만든다.
    public String hash(
            Purpose purpose,
            String challengeId,
            String rawCode) {
        String normalized = codeGenerator.normalize(rawCode);

        if(!codeGenerator.isValid(normalized)) {
            throw new IllegalArgumentException(
                    "인증 코드 형식이 올바르지 않습니다."
            );
        }
        return HexFormat.of().formatHex(
                calculateHmac(purpose, challengeId, normalized)
        );
    }

    // ────────────────────────── 코드 확인 ──────────────────────────

    /**
     *  사용자가 입력한 코드와 DB에 저장된 HMAC을 상수 시간 방식으로 비교한다.
     *  이유 - 공격자가 입력값을 넣으면서 반복 측정을 통해 걸리는 시간에 따라
     *        값을 유추할 수 있기 때문 (보조 방어수단)
     */
    public boolean matches(
            Purpose purpose,
            String challengeId,
            String rawInput,
            String storedHash) {

        if(purpose == null || challengeId == null ||
                storedHash == null || storedHash.length() != 64) {
            return false;
        }

        String normalized = codeGenerator.normalize(rawInput);
        if(!codeGenerator.isValid(normalized)) {
            return false;
        }

        try {
            byte[] expected = HexFormat.of().parseHex(storedHash);
            byte[] actual = calculateHmac(
                    purpose,
                    challengeId,
                    normalized
            );

            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    private byte[] calculateHmac(
            Purpose purpose,
            String challengeId,
            String normalizedCode) {

        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(challengeId, "challengeId");

        String message = purpose.name()
                + '\0'
                + challengeId
                + '\0'
                + normalizedCode;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);

            return mac.doFinal(
                    message.getBytes(StandardCharsets.UTF_8)
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    HMAC_ALGORITHM + "을 사용할 수 없는 환경입니다. ", e
            );
        }
    }

    // ────────────────────────── 시크릿 생성 ──────────────────────────

    private SecretKeySpec createKey(String base64Secret) {
        if(base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "app.auth.code-hmac-secret 설정이 필요합니다."
            );
        }
        byte[] secret;

        try {
            secret = Base64.getDecoder().decode(base64Secret);
        }catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.auth.code-hmac-secret은 Base64 형식이어야 합니다.",
                    e
            );
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.auth.code-hmac-secret은 "
                            + "디코딩 후 32바이트 이상이어야 합니다."
            );
        }

        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }
}
