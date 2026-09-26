package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14 — Matrice de permissions administratives.
 * GESTIONNAIRE < ADMIN < SUPER_ADMIN. Cœur pur (sans base).
 */
class AdminRoleTest {

    @Test
    void hierarchyLevels() {
        assertTrue(AdminRole.level("SUPER_ADMIN") > AdminRole.level("ADMIN"));
        assertTrue(AdminRole.level("ADMIN") > AdminRole.level("GESTIONNAIRE"));
        assertEquals(0, AdminRole.level(null));
        assertEquals(0, AdminRole.level("CLIENT"));
        assertEquals(0, AdminRole.level("SUPER_ADMIN "));
    }

    @Test
    void gestionnaireIsReadAndOperationalOnly() {
        // Lecture + opérationnel : oui (action inconnue = permise).
        assertTrue(AdminRole.allows("GESTIONNAIRE", "READ"));
        assertTrue(AdminRole.allows("GESTIONNAIRE", "ORDER_STATUS"));
        // Argent / destructif / diffusion : non.
        assertFalse(AdminRole.allows("GESTIONNAIRE", AdminRole.MONEY));
        assertFalse(AdminRole.allows("GESTIONNAIRE", AdminRole.DESTRUCTIVE));
        assertFalse(AdminRole.allows("GESTIONNAIRE", AdminRole.BROADCAST));
        assertFalse(AdminRole.allows("GESTIONNAIRE", AdminRole.ROLE_MGMT));
    }

    @Test
    void adminCanDoEverythingExceptRoleManagement() {
        assertTrue(AdminRole.allows("ADMIN", AdminRole.MONEY));
        assertTrue(AdminRole.allows("ADMIN", AdminRole.DESTRUCTIVE));
        assertTrue(AdminRole.allows("ADMIN", AdminRole.BROADCAST));
        assertFalse(AdminRole.allows("ADMIN", AdminRole.ROLE_MGMT));
    }

    @Test
    void superAdminCanDoEverything() {
        assertTrue(AdminRole.allows("SUPER_ADMIN", AdminRole.MONEY));
        assertTrue(AdminRole.allows("SUPER_ADMIN", AdminRole.DESTRUCTIVE));
        assertTrue(AdminRole.allows("SUPER_ADMIN", AdminRole.BROADCAST));
        assertTrue(AdminRole.allows("SUPER_ADMIN", AdminRole.ROLE_MGMT));
    }

    @Test
    void unknownOrNullRoleCanDoNothing() {
        assertFalse(AdminRole.allows(null, AdminRole.MONEY));
        assertFalse(AdminRole.allows(null, "READ"));
        assertFalse(AdminRole.allows("SUPPLIER", "READ"));
    }
}
