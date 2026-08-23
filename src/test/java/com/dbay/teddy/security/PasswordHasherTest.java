package com.dbay.teddy.security;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    @Test
    public void verifiesOnlyTheExpectedPassword() {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String encoded = PasswordHasher.hash("correct horse", salt, 10000);

        assertTrue(PasswordHasher.verify("correct horse", encoded));
        assertFalse(PasswordHasher.verify("wrong horse", encoded));
        assertFalse(PasswordHasher.verify(null, encoded));
    }

    @Test
    public void rejectsMalformedOrWeakHashes() {
        assertFalse(PasswordHasher.verify("password", ""));
        assertFalse(PasswordHasher.verify("password", "pbkdf2-sha256$1$bad$bad"));
        assertFalse(PasswordHasher.verify("password", "sha256$10000$bad$bad"));
    }
}
