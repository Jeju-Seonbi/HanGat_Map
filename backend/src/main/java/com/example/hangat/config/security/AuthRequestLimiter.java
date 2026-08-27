package com.example.hangat.config.security;

import com.example.hangat.common.util.EmailNormalizer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AuthRequestLimiter {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final Duration EMAIL_WINDOW = Duration.ofMinutes(10);
    private static final int LOGIN_PER_EMAIL = 10;
    private static final int LOGIN_PER_IP = 30;
    private static final int EMAIL_PER_ADDRESS = 3;
    private static final int EMAIL_PER_IP = 10;
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void checkLogin(String clientIp, String email) {
        check("login:email", EmailNormalizer.normalize(email), LOGIN_PER_EMAIL, LOGIN_WINDOW);
        check("login:ip", clientIp, LOGIN_PER_IP, LOGIN_WINDOW);
    }

    public void checkEmailRequest(String action, String clientIp, String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        check(action + ":email", normalizedEmail, EMAIL_PER_ADDRESS, EMAIL_WINDOW);
        check(action + ":ip", clientIp, EMAIL_PER_IP, EMAIL_WINDOW);
    }

    private void check(String scope, String subject, int limit, Duration window) {
        long now = System.currentTimeMillis();
        String key = scope + ':' + TokenHasher.hash(subject == null ? "unknown" : subject);
        removeExpiredIfNeeded(now);

        if (counters.size() >= MAX_TRACKED_KEYS && !counters.containsKey(key)) {
            throw new AuthRateLimitException();
        }

        AtomicBoolean rejected = new AtomicBoolean(false);
        counters.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAtMillis() <= now) {
                return new WindowCounter(1, now + window.toMillis());
            }
            if (current.count() >= limit) {
                rejected.set(true);
                return current;
            }
            return new WindowCounter(current.count() + 1, current.expiresAtMillis());
        });

        if (rejected.get()) {
            throw new AuthRateLimitException();
        }
    }

    private void removeExpiredIfNeeded(long now) {
        if (counters.size() < 10_000) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private record WindowCounter(int count, long expiresAtMillis) {
    }
}
