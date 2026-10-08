package com.ecommerce.service;

import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Shipment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow livraison v2 : gardes pures (sans DB).
 * Les transitions persistées sont testées en E2E (API + MySQL).
 */
class DeliveryFlowTest {

    private Order order(String paymentMethod) {
        Order o = new Order();
        o.setPaymentMethod(paymentMethod);
        return o;
    }

    private Payment pay(String status) {
        Payment p = new Payment();
        p.setStatus(status);
        return p;
    }

    @Test
    void paiementConfirmeLigneReussie() {
        assertTrue(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_MTN"), pay("REUSSIE")));
        assertTrue(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_ORANGE"), pay("COMPLETED")));
    }

    @Test
    void paiementNonConfirmeSiEnAttenteOuEchoue() {
        assertFalse(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_MTN"), pay("EN_ATTENTE")));
        assertFalse(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_MTN"), pay("ECHOUEE")));
        assertFalse(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_MTN"), pay("ANNULEE")));
        assertFalse(DeliveryFlowService.isPaymentConfirmed(order("MOBILE_MONEY_MTN"), null));
    }

    @Test
    void paiementALaLivraisonToujoursEligibleAuDepart() {
        // Le cash est soldé à la livraison : le départ ne doit pas le bloquer.
        assertTrue(DeliveryFlowService.isPaymentConfirmed(order("PAIEMENT_LIVRAISON"), pay("EN_ATTENTE")));
        assertTrue(DeliveryFlowService.isPaymentConfirmed(order("PAIEMENT_LIVRAISON"), null));
    }

    @Test
    void commandeHistoriqueWhatsappSansLigneToleree() {
        assertTrue(DeliveryFlowService.isPaymentConfirmed(order("WHATSAPP"), null));
        assertFalse(DeliveryFlowService.isPaymentConfirmed(order("WHATSAPP"), pay("ECHOUEE")));
    }

    @Test
    void ordreNulJamaisConfirme() {
        assertFalse(DeliveryFlowService.isPaymentConfirmed(null, pay("REUSSIE")));
    }

    @Test
    void flagsLivraisonShipment() {
        Shipment s = new Shipment();
        assertFalse(s.isDeliveryStarted());
        assertFalse(s.isDeliveryValidated());
        s.setDeliveryStartedAt("2026-10-06 12:00:00");
        assertTrue(s.isDeliveryStarted());
        assertFalse(s.isDeliveryValidated());
        s.setDeliveryValidatedAt("2026-10-06 13:00:00");
        assertTrue(s.isDeliveryValidated());
    }

    @Test
    void codesNotificationWorkflowConnus() {
        for (String code : new String[]{"DELIVERY_STARTED", "RECEIPT_CONFIRMED", "DELIVERY_VALIDATED"}) {
            assertTrue(NotificationType.isKnown(code), code);
            assertEquals("DELIVERY", NotificationType.categoryOf(code), code);
        }
        for (String code : new String[]{"PAYOUT_REQUESTED", "PAYOUT_COMPLETED"}) {
            assertTrue(NotificationType.isKnown(code), code);
            assertEquals("PAYMENT", NotificationType.categoryOf(code), code);
        }
        assertEquals("HIGH", NotificationType.defaultPriorityOf("DELIVERY_STARTED"));
        assertEquals("HIGH", NotificationType.defaultPriorityOf("RECEIPT_CONFIRMED"));
    }

    @Test
    void constantesEvenementsStables() {
        // Le frontend et l'audit s'appuient sur ces chaînes exactes.
        assertEquals("DELIVERY_STARTED", DeliveryFlowService.EV_STARTED);
        assertEquals("RECEIPT_CONFIRMED", DeliveryFlowService.EV_RECEIPT);
        assertEquals("DELIVERY_VALIDATED", DeliveryFlowService.EV_VALIDATED);
    }
}
