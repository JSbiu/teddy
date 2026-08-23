package com.dbay.teddy.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

public final class PasswordHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int MIN_ITERATIONS = 10000;
    private static final int MAX_ITERATIONS = 1000000;

    private PasswordHasher() {
    }

    public static boolean verify(String password, String encodedHash) {
        if (password == null || encodedHash == null) {
            return false;
        }

        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length < 16 || expected.length != KEY_LENGTH_BITS / 8) {
                return false;
            }
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String hash(String password, byte[] salt, int iterations) {
        if (password == null || salt == null || salt.length < 16) {
            throw new IllegalArgumentException("Password and at least 16 bytes of salt are required");
        }
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("PBKDF2 iteration count is outside the supported range");
        }
        byte[] derived = derive(password.toCharArray(), salt.clone(), iterations);
        return PREFIX + "$" + iterations + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
