package com.example.hangat.course;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.config.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseClaimTokenServiceTest {

    private static final String SECRET =
            "test-root-secret-that-is-long-enough-for-hmac-signing-0123456789";

    @Test
    void signedProofIsBoundToCourseAndExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        CourseClaimTokenService service = new CourseClaimTokenService(SECRET, 60_000, clock);
        CourseClaimTokenService.ClaimProof proof = service.issue(11L);

        service.validate(proof.token(), 11L);
        assertThat(proof.expiresAt()).isEqualTo(Instant.parse("2026-08-31T00:01:00Z"));
        assertThatThrownBy(() -> service.validate(proof.token(), 12L))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_CLAIM_INVALID));

        clock.instant = Instant.parse("2026-08-31T00:02:00Z");
        assertThatThrownBy(() -> service.validate(proof.token(), 11L))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_CLAIM_EXPIRED));
    }

    @Test
    void tamperingAndAccessTokenConfusionAreRejected() {
        CourseClaimTokenService service = new CourseClaimTokenService(
                SECRET, 60_000, Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneId.of("UTC")));
        String proof = service.issue(11L).token();

        assertThatThrownBy(() -> service.validate(proof + "x", 11L))
                .isInstanceOf(BaseException.class);

        JwtProvider accessTokens = new JwtProvider(SECRET, 60_000);
        assertThatThrownBy(() -> service.validate(accessTokens.createAccessToken(11L), 11L))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_CLAIM_INVALID));
        assertThatThrownBy(() -> accessTokens.parseUserId(proof))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.JWT_INVALID));

        assertThatThrownBy(() -> service.validate(wrongPurposeToken(), 11L))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_CLAIM_INVALID));
    }

    private String wrongPurposeToken() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] key = mac.doFinal("hangat/course-claim/v1".getBytes(StandardCharsets.UTF_8));
            return Jwts.builder().subject("11").claim("purpose", "ACCESS")
                    .id("test-nonce").issuedAt(Date.from(Instant.parse("2026-08-31T00:00:00Z")))
                    .expiration(Date.from(Instant.parse("2099-08-31T00:01:00Z")))
                    .signWith(Keys.hmacShaKeyFor(key)).compact();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
