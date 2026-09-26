package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12 : machine d'état des expéditions (pure, sans DB).
 */
class ShipmentStatusTest {

    @Test
    void chaineNominale() {
        assertTrue(ShipmentStatus.canTransition("PREPARING", "READY_FOR_PICKUP"));
        assertTrue(ShipmentStatus.canTransition("READY_FOR_PICKUP", "PICKED_UP"));
        assertTrue(ShipmentStatus.canTransition("PICKED_UP", "IN_TRANSIT"));
        assertTrue(ShipmentStatus.canTransition("IN_TRANSIT", "OUT_FOR_DELIVERY"));
        assertTrue(ShipmentStatus.canTransition("OUT_FOR_DELIVERY", "DELIVERED"));
    }

    @Test
    void transitionsInterdites() {
        assertFalse(ShipmentStatus.canTransition("PREPARING", "DELIVERED"));
        assertFalse(ShipmentStatus.canTransition("DELIVERED", "PREPARING"));
        assertFalse(ShipmentStatus.canTransition("DELIVERED", "IN_TRANSIT"));
        assertFalse(ShipmentStatus.canTransition("CANCELLED", "PREPARING"));
        assertFalse(ShipmentStatus.canTransition("PREPARING", "FLY"));
        assertFalse(ShipmentStatus.canTransition(null, "DELIVERED"));
    }

    @Test
    void echecEtRetour() {
        assertTrue(ShipmentStatus.canTransition("IN_TRANSIT", "FAILED"));
        assertTrue(ShipmentStatus.canTransition("OUT_FOR_DELIVERY", "RETURNED"));
        assertTrue(ShipmentStatus.canTransition("PREPARING", "CANCELLED"));
        assertFalse(ShipmentStatus.canTransition("FAILED", "DELIVERED"));
    }

    @Test
    void idempotence() {
        assertTrue(ShipmentStatus.canTransition("DELIVERED", "DELIVERED"));
        assertTrue(ShipmentStatus.isKnown("OUT_FOR_DELIVERY"));
        assertFalse(ShipmentStatus.isKnown("FLY"));
    }
}
