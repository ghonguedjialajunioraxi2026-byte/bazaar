package com.ecommerce.pawapay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garde du mode TEST : résolu depuis l'environnement, sandbox par défaut,
 * jamais de production automatique (le bouton TEST et test-confirm
 * s'appuient sur isSandbox()).
 */
class PawaPayTestModeTest {

    @Test
    void defautSandbox() {
        assertEquals("sandbox", PawaPayConfig.resolveEnvironment(null));
        assertEquals("sandbox", PawaPayConfig.resolveEnvironment(""));
        assertEquals("sandbox", PawaPayConfig.resolveEnvironment("  "));
    }

    @Test
    void productionExpliciteUniquement() {
        assertEquals("production", PawaPayConfig.resolveEnvironment("production"));
        assertEquals("production", PawaPayConfig.resolveEnvironment(" PRODUCTION "));
    }

    @Test
    void sandboxExplicite() {
        assertEquals("sandbox", PawaPayConfig.resolveEnvironment("sandbox"));
    }
}
