package com.example.hangat.config.security.jwt;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 *  Access 토큰 발급이랑 파싱
 *  JWT는 서명만하고 각 클라이언트에 저장함.
 *  refresh는 서버에 저장함.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    @Getter
    private final long accessTokenTtlMs;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-ttl-ms:600000}") long accessTokenTtlMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
    }

    // userId만 담음. JWT는 암호화가 아니라 서명이라 Base64만 풀면 다 보임.
    public String createAccessToken(Long userId) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenTtlMs))
                .signWith(key)
                .compact();
    }

    // 만료랑 위조를 걸러서 BaseException으로 바꿔줌.
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new BaseException(BaseResponseStatus.JWT_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BaseException(BaseResponseStatus.JWT_INVALID);
        }
    }
}
