package com.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Socle Phase 10 : arithmétique entière FCFA, HALF_UP, sans DB.
 */
class MonetizationServiceTest {

    @Test
    void commissionSimple() {
        assertEquals(10000, MonetizationService.commissionOf(100000, 1000));
        assertEquals(90000, 100000 - MonetizationService.commissionOf(100000, 1000));
    }

    @Test
    void arrondiHalfUp() {
        // 9999 * 10 % = 999,9 -> 1000
        assertEquals(1000, MonetizationService.commissionOf(9999, 1000));
        // 9994 * 10 % = 999,4 -> 999
        assertEquals(999, MonetizationService.commissionOf(9994, 1000));
    }

    @Test
    void bornes() {
        assertEquals(0, MonetizationService.commissionOf(0, 1000));
        assertEquals(0, MonetizationService.commissionOf(100000, 0));
        assertEquals(0, MonetizationService.commissionOf(-5, 1000));
        assertEquals(100000, MonetizationService.commissionOf(100000, 10000));
    }
}
