package com.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15 — Anti brute-force : 5 échecs → blocage 15 min, succès = reset.
 */
class LoginAttemptServiceTest {

    private LoginAttemptService svc;

    @BeforeEach
    void setup() {
        svc = LoginAttemptService.get();
        svc.clear();
    }

    @Test
    void cinqEchecsBloqueLeSixieme() {
        String key = LoginAttemptService.key("admin", "Admin", "1.2.3.4");
        for (int i = 0; i < 4; i++) {
            svc.recordFailure(key);
            assertFalse(svc.isBlocked(key));
        }
        svc.recordFailure(key);
        assertTrue(svc.isBlocked(key));
    }

    @Test
    void succesReinitialiseLeCompteur() {
        String key = LoginAttemptService.key("supplier", "a@b.cm", "5.6.7.8");
        for (int i = 0; i < 4; i++) svc.recordFailure(key);
        svc.recordSuccess(key);
        assertFalse(svc.isBlocked(key));
        // Le compteur est reparti : 4 nouveaux échecs ne bloquent pas.
        for (int i = 0; i < 4; i++) svc.recordFailure(key);
        assertFalse(svc.isBlocked(key));
    }

    @Test
    void cleInsensibleALaCasseEtIsoleeParIp() {
        String k1 = LoginAttemptService.key("admin", "Admin", "1.1.1.1");
        String k2 = LoginAttemptService.key("admin", "admin", "2.2.2.2");
        for (int i = 0; i < 5; i++) svc.recordFailure(k1);
        assertTrue(svc.isBlocked(k1));
        assertFalse(svc.isBlocked(k2));
    }

    @Test
    void clesNullesSansEffet() {
        assertFalse(svc.isBlocked(null));
        svc.recordFailure(null);
        svc.recordSuccess(null);
    }
}
