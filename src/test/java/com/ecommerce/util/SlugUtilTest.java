package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Socle de tests Phase 2 : utilitaires purs (aucune DB requise).
 */
class SlugUtilTest {

    @Test
    void accentsFrancais() {
        assertEquals("electronique-high-tech", SlugUtil.toSlug("Électronique & High-Tech"));
        assertEquals("montres-accessoires", SlugUtil.toSlug("Montres & Accessoires"));
    }

    @Test
    void casLimites() {
        assertEquals("", SlugUtil.toSlug(null));
        assertEquals("categorie", SlugUtil.toSlug("!!!"));
        assertEquals("robe-wax", SlugUtil.toSlug("  Robe   Wax  "));
    }
}
