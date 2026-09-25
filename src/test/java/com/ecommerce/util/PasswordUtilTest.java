package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Socle de tests Phase 2 : hash/verify (aucune DB requise).
 */
class PasswordUtilTest {

    @Test
    void hashEtVerify() {
        String hash = PasswordUtil.hash("admin123");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertTrue(PasswordUtil.verify("admin123", hash));
        assertFalse(PasswordUtil.verify("mauvais", hash));
    }

    @Test
    void hashDeterministe() {
        assertEquals(PasswordUtil.hash("test"), PasswordUtil.hash("test"));
    }
}
