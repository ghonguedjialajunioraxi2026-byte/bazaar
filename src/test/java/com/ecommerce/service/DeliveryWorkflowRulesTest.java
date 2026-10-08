package com.ecommerce.service;

import com.ecommerce.pawapay.PawaPayPayoutService;
import com.ecommerce.util.OrderStatus;
import com.ecommerce.util.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gardes du workflow livraison v2 (sans DB) :
 * seul chemin vers le payout = CONFIRMEE -> EN_PREPARATION
 * -> EN_LIVRAISON -> LIVREE, piloté par
 * start-delivery / confirm-receipt / validate-delivery.
 */
class DeliveryWorkflowRulesTest {

    @Test
    void seulCheminVersPayout() {
        assertTrue(OrderStatus.canTransition("CONFIRMEE", "EN_PREPARATION"));
        assertTrue(OrderStatus.canTransition("EN_PREPARATION", "EN_LIVRAISON"));
        assertTrue(OrderStatus.canTransition("EN_LIVRAISON", "LIVREE"));
    }

    @Test
    void raccourcisEtResurrectionsRefuses() {
        assertFalse(OrderStatus.canTransition("EN_ATTENTE", "EN_LIVRAISON"));
        assertFalse(OrderStatus.canTransition("EN_ATTENTE", "LIVREE"));
        assertFalse(OrderStatus.canTransition("CONFIRMEE", "LIVREE"));
        assertFalse(OrderStatus.canTransition("LIVREE", "EN_LIVRAISON"));
        assertFalse(OrderStatus.canTransition("ANNULEE", "CONFIRMEE"));
        assertFalse(OrderStatus.canTransition("LIVREE", "ANNULEE"));
    }

    @Test
    void pasDeLivraisonDirecteSansWorkflow() {
        // L'ancien passage direct PREPARING -> DELIVERED est interdit :
        // seule la validation après confirmation client solde.
        assertFalse(ShipmentStatus.canTransition("PREPARING", "DELIVERED"));
        assertFalse(ShipmentStatus.canTransition("DELIVERED", "PREPARING"));
    }

    @Test
    void codesBlocagePayoutStables() {
        // Règle financière absolue : chaque condition manquante
        // a son code de blocage (jamais de payout sinon).
        assertEquals("ORDER_NOT_DELIVERED", PawaPayPayoutService.BLOCK_ORDER_NOT_DELIVERED);
        assertEquals("RECEIPT_NOT_CONFIRMED", PawaPayPayoutService.BLOCK_RECEIPT_NOT_CONFIRMED);
        assertEquals("DISPUTE_OPEN", PawaPayPayoutService.BLOCK_DISPUTE_OPEN);
        assertEquals("REFUND_IN_PROGRESS", PawaPayPayoutService.BLOCK_REFUND_IN_PROGRESS);
        assertEquals("NO_SALE_ENTRY", PawaPayPayoutService.BLOCK_NO_SALE_ENTRY);
        assertEquals("DEPOSIT_NOT_COMPLETED", PawaPayPayoutService.BLOCK_DEPOSIT_NOT_COMPLETED);
    }

    @Test
    void notificationsWorkflowCompletes() {
        for (String code : new String[]{"DELIVERY_STARTED", "RECEIPT_CONFIRMED",
                "DELIVERY_VALIDATED", "PAYOUT_REQUESTED", "PAYOUT_COMPLETED"}) {
            assertTrue(NotificationType.isKnown(code), code);
        }
    }

    @Test
    void commissionUnPourcentCible() {
        // 100 000 FCFA × 1 % = 1 000 FCFA (règle active = 100 bps).
        assertEquals(1000, MonetizationService.commissionOf(100000, 100));
    }
}
