package com.eduaccess.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void verifiesTheCorrectPasswordOnly() {
        String stored = PasswordHasher.hash("correct horse".toCharArray());

        assertTrue(PasswordHasher.verify("correct horse".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("wrong password".toCharArray(), stored));
    }

    @Test
    void samePasswordGetsAFreshSalt() {
        assertNotEquals(PasswordHasher.hash("same".toCharArray()), PasswordHasher.hash("same".toCharArray()));
    }

    @Test
    void malformedStoredValueFailsClosed() {
        assertFalse(PasswordHasher.verify("anything".toCharArray(), "plain-text-not-a-hash"));
    }
}
