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
    void hashSaleChaqueHachageEstUnique() {
        // Sel aléatoire : deux hash du même mot de passe diffèrent, mais vérifient tous deux.
        String h1 = PasswordUtil.hash("test");
        String h2 = PasswordUtil.hash("test");
        assertNotEquals(h1, h2);
        assertTrue(PasswordUtil.verify("test", h1));
        assertTrue(PasswordUtil.verify("test", h2));
    }
}
