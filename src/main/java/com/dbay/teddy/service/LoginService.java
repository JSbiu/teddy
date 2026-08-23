package com.dbay.teddy.service;

import com.dbay.teddy.security.PasswordHasher;
import com.dbay.teddy.security.SessionManager;
import com.dbay.teddy.utils.TeddyConf;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class LoginService {
    public static final String SESSION_COOKIE_NAME = "token";
    private static final long DEFAULT_SESSION_TTL_SECONDS = 28800L;
    private static final long MAX_SESSION_TTL_SECONDS = 604800L;

    private final SessionManager sessionManager;

    public LoginService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public boolean isConfigured() {
        return !TeddyConf.get("auth.username", "").trim().isEmpty()
                && !TeddyConf.get("auth.password-hash", "").trim().isEmpty();
    }

    public boolean checkPassword(String userName, String password) {
        String configuredUser = TeddyConf.get("auth.username", "").trim();
        String configuredHash = TeddyConf.get("auth.password-hash", "").trim();
        boolean userMatches = constantTimeEquals(configuredUser, userName);
        boolean passwordMatches = PasswordHasher.verify(password, configuredHash);
        return userMatches && passwordMatches;
    }

    public boolean checkToken(String token) {
        return sessionManager.isValid(token);
    }

    public String createToken() {
        return sessionManager.createToken(getSessionTtlSeconds());
    }

    public void invalidateToken(String token) {
        sessionManager.invalidate(token);
    }

    public String getCookieValue(HttpServletRequest request, String cookieName) {
        if (cookieName == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    public int getSessionMaxAgeSeconds() {
        return Math.toIntExact(getSessionTtlSeconds());
    }

    public boolean isSecureCookie() {
        return Boolean.parseBoolean(TeddyConf.get("auth.cookie.secure", "false"));
    }

    private long getSessionTtlSeconds() {
        String configured = TeddyConf.get("auth.session.ttl-seconds",
                String.valueOf(DEFAULT_SESSION_TTL_SECONDS)).trim();
        try {
            long value = Long.parseLong(configured);
            if (value >= 60L && value <= MAX_SESSION_TTL_SECONDS) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_SESSION_TTL_SECONDS;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
