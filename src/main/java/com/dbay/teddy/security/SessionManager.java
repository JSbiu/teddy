package com.dbay.teddy.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Map<String, Long> expiresAtByToken = new ConcurrentHashMap<>();

    public SessionManager() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    SessionManager(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String createToken(long ttlSeconds) {
        if (ttlSeconds < 60) {
            throw new IllegalArgumentException("Session TTL must be at least 60 seconds");
        }
        removeExpired();
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        expiresAtByToken.put(token, clock.millis() + ttlSeconds * 1000L);
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        Long expiresAt = expiresAtByToken.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= clock.millis()) {
            expiresAtByToken.remove(token, expiresAt);
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        if (token != null) {
            expiresAtByToken.remove(token);
        }
    }

    private void removeExpired() {
        long now = clock.millis();
        expiresAtByToken.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
