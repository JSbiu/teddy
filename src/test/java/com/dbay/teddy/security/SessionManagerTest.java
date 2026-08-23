package com.dbay.teddy.security;

import org.junit.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionManagerTest {
    @Test
    public void createsUniqueTokensAndInvalidatesThem() {
        MutableClock clock = new MutableClock();
        SessionManager sessions = new SessionManager(new SecureRandom(), clock);

        String first = sessions.createToken(60);
        String second = sessions.createToken(60);

        assertNotEquals(first, second);
        assertTrue(sessions.isValid(first));
        sessions.invalidate(first);
        assertFalse(sessions.isValid(first));
        assertTrue(sessions.isValid(second));
    }

    @Test
    public void expiresTokensAtTheConfiguredDeadline() {
        MutableClock clock = new MutableClock();
        SessionManager sessions = new SessionManager(new SecureRandom(), clock);
        String token = sessions.createToken(60);

        clock.advanceSeconds(59);
        assertTrue(sessions.isValid(token));
        clock.advanceSeconds(1);
        assertFalse(sessions.isValid(token));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-23T00:00:00Z");

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
