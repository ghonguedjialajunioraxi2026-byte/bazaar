package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15 — Sécurité des mots de passe : PBKDF2 salé, compat héritage,
 * comparaisons sûres. Aucune DB requise.
 */
class PasswordSecurityTest {

    @Test
    void hashModerneAuFormatPbkdf2() {
        String h = PasswordUtil.hash("S3cret!2026");
        assertTrue(h.startsWith("pbkdf2$"));
        assertEquals(4, h.split("\\$").length);
        assertFalse(PasswordUtil.needsRehash(h));
    }

    @Test
    void legacySha256ToujoursVerifiablePuisMigrable() {
        // Hash historique (database.sql : admin/admin123).
        String legacy = "JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk=";
        assertTrue(PasswordUtil.verify("admin123", legacy));
        assertFalse(PasswordUtil.verify("autre", legacy));
        assertTrue(PasswordUtil.needsRehash(legacy));
    }

    @Test
    void verifyRobusteAuxEntreesNulles() {
        assertFalse(PasswordUtil.verify(null, "pbkdf2$1$abc$def"));
        assertFalse(PasswordUtil.verify("x", null));
        assertFalse(PasswordUtil.verify("x", "format-invalide"));
        assertFalse(PasswordUtil.verify("x", "pbkdf2$notanumber$abc$def"));
    }

    @Test
    void motDePasseVideNeValideJamaisUnHash() {
        String h = PasswordUtil.hash("non-vide");
        assertFalse(PasswordUtil.verify("", h));
    }
}
