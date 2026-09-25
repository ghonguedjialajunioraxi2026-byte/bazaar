package com.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11 : validation providers purs (aucune DB, aucun réseau).
 */
class PaymentProvidersTest {

    @Test
    void methodesProposees() {
        assertTrue(PaymentProviders.offeredMethods().contains("PAIEMENT_LIVRAISON"));
        assertTrue(PaymentProviders.offeredMethods().contains("MOBILE_MONEY_MTN"));
        assertTrue(PaymentProviders.offeredMethods().contains("MOBILE_MONEY_ORANGE"));
        assertFalse(PaymentProviders.offeredMethods().contains("CART_BANCAIRE"));
    }

    @Test
    void providerInconnuRejete() {
        assertNull(PaymentProviders.forMethod("FAKE"));
        assertNull(PaymentProviders.forMethod(null));
    }

    @Test
    void referenceMomo() {
        PaymentProvider momo = PaymentProviders.forMethod("MOBILE_MONEY_MTN");
        assertTrue(momo.isReferenceValid("MP250915.987654"));
        assertTrue(momo.isReferenceValid("OM-ABCDEF1234"));
        assertFalse(momo.isReferenceValid("ab"));
        assertFalse(momo.isReferenceValid("...."));
        assertFalse(momo.isReferenceValid(null));
        assertFalse(momo.isReferenceValid("<script>alert(1)</script>"));
    }

    @Test
    void codSansReference() {
        PaymentProvider cod = PaymentProviders.forMethod("PAIEMENT_LIVRAISON");
        assertEquals("COD", cod.getId());
        assertFalse(cod.autoConfirmable());
    }
}
