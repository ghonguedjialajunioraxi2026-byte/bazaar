package com.ecommerce.pawapay;

import com.ecommerce.model.Payment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logique pure PawaPay : références, MSISDN, statuts, masquage.
 * Aucune DB, aucun réseau, aucun token.
 */
class PawaPayServiceLogicTest {

    @Test
    void referenceInterneUniqueEtFormattee() {
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String ref = PawaPayPaymentService.buildInternalReference();
            assertTrue(ref.matches("MBOA-ORD-\\d{8}-[A-Z2-9]{8}"), ref);
            assertTrue(refs.add(ref), "référence dupliquée : " + ref);
        }
    }

    @Test
    void normalisationMsisdn() {
        assertEquals("237690123456", PawaPayPaymentService.normalizeMsisdn("690123456"));
        assertEquals("237690123456", PawaPayPaymentService.normalizeMsisdn("0690123456"));
        assertEquals("237690123456", PawaPayPaymentService.normalizeMsisdn("+237690123456"));
        assertEquals("237690123456", PawaPayPaymentService.normalizeMsisdn("00237690123456"));
        assertEquals("237690123456", PawaPayPaymentService.normalizeMsisdn("237 690 12 34 56"));
        assertNull(PawaPayPaymentService.normalizeMsisdn("123"));
        assertNull(PawaPayPaymentService.normalizeMsisdn("012345678"));
        assertNull(PawaPayPaymentService.normalizeMsisdn("abcdef"));
        assertNull(PawaPayPaymentService.normalizeMsisdn(null));
    }

    @Test
    void statutsFinaux() {
        assertTrue(PawaPayPaymentService.isFinalStatus("COMPLETED"));
        assertTrue(PawaPayPaymentService.isFinalStatus("FAILED"));
        assertTrue(PawaPayPaymentService.isFinalStatus("REUSSIE"));
        assertFalse(PawaPayPaymentService.isFinalStatus("ACCEPTED"));
        assertFalse(PawaPayPaymentService.isFinalStatus("PROCESSING"));
        assertFalse(PawaPayPaymentService.isFinalStatus("PENDING"));
        assertFalse(PawaPayPaymentService.isFinalStatus("EN_ATTENTE"));
    }

    @Test
    void vueMasqueeSansTelephoneClair() {
        Payment p = new Payment();
        p.setId(1);
        p.setOrderId(2);
        p.setMethod("MOBILE_MONEY_MTN");
        p.setProvider("PAWAPAY");
        p.setOperator("MTN_MOMO_CMR");
        p.setAmount(525000);
        p.setCurrency("XAF");
        p.setStatus("PROCESSING");
        p.setInternalReference("MBOA-ORD-20260930-ABCDEFGH");
        p.setProviderTransactionId("afb57b93-7849-49aa-babb-4c3ccbfe3d79");
        p.setPhoneNumber("237690123456");
        Map<String, Object> view = PawaPayPaymentService.maskedView(p);
        assertEquals("***456", view.get("phoneMasked"));
        assertFalse(view.toString().contains("237690123456"));
        assertFalse(view.toString().contains("Bearer"));
    }

    @Test
    void motifEchecSansSecret() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failureCode", "AMOUNT_OUT_OF_BOUNDS");
        failure.put("failureMessage", "Amount outside limits");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("failureReason", failure);
        String reason = PawaPayPaymentService.failureOf(node);
        assertTrue(reason.contains("AMOUNT_OUT_OF_BOUNDS"));
        assertNull(PawaPayPaymentService.failureOf(null));
    }
}
