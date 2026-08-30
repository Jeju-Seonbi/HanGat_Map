package com.example.hangat.course;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class CourseClaimTokenService {

    static final String PURPOSE = "COURSE_CLAIM";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final byte[] KEY_CONTEXT =
            "hangat/course-claim/v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;
    private final long ttlMs;
    private final Clock clock;

    @Autowired
    public CourseClaimTokenService(
            @Value("${jwt.secret}") String rootSecret,
            @Value("${course.claim.ttl-ms:1800000}") long ttlMs
    ) {
        this(rootSecret, ttlMs, Clock.systemUTC());
    }

    CourseClaimTokenService(String rootSecret, long ttlMs, Clock clock) {
        if (rootSecret == null || rootSecret.isBlank()) {
            throw new IllegalArgumentException("claim signing root secret is required");
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("course.claim.ttl-ms must be positive");
        }
        this.key = Keys.hmacShaKeyFor(deriveKey(rootSecret));
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    public ClaimProof issue(Long courseId) {
        if (courseId == null) {
            throw new IllegalArgumentException("courseId is required");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(ttlMs);
        String token = Jwts.builder()
                .subject(String.valueOf(courseId))
                .claim(PURPOSE_CLAIM, PURPOSE)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new ClaimProof(token, expiresAt);
    }

    public void validate(String token, Long expectedCourseId) {
        if (token == null || token.isBlank() || expectedCourseId == null) {
            throw new BaseException(BaseResponseStatus.COURSE_CLAIM_INVALID);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))
                    || !String.valueOf(expectedCourseId).equals(claims.getSubject())
                    || claims.getId() == null
                    || claims.getId().isBlank()) {
                throw new BaseException(BaseResponseStatus.COURSE_CLAIM_INVALID);
            }
        } catch (ExpiredJwtException exception) {
            throw new BaseException(BaseResponseStatus.COURSE_CLAIM_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BaseException(BaseResponseStatus.COURSE_CLAIM_INVALID);
        }
    }

    private static byte[] deriveKey(String rootSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rootSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(KEY_CONTEXT);
        } catch (Exception exception) {
            throw new IllegalStateException("claim signing key derivation failed", exception);
        }
    }

    public record ClaimProof(String token, Instant expiresAt) {
    }
}
