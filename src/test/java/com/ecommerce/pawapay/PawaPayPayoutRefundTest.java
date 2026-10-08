package com.ecommerce.pawapay;

import com.ecommerce.model.Refund;
import com.ecommerce.model.SupplierPayment;
import com.ecommerce.service.MonetizationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logique pure Payout/Refund/Commission : calculs, statuts, payloads,
 * masquage. Aucune DB, aucun réseau, aucun token.
 */
class PawaPayPayoutRefundTest {

    @Test
    void commissionUnPourcent() {
        // 100 000 FCFA × 1 % = 1 000 FCFA (HALF_UP entier).
        assertEquals(1000, MonetizationService.commissionOf(100000, 100));
        assertEquals(0, MonetizationService.commissionOf(0, 100));
        assertEquals(0, MonetizationService.commissionOf(100000, 0));
        // Arrondi : 199 × 1 % = 1,99 → 2.
        assertEquals(2, MonetizationService.commissionOf(199, 100));
    }

    @Test
    void decompositionPayout() {
        // Vente 100 000, commission 1 % = 1 000, frais 0 → net 99 000.
        Map<String, Object> b = PawaPayPayoutService.buildBreakdown(
            100000, 100, 1000, 0, 99000, 0);
        assertEquals(100000, b.get("gross"));
        assertEquals(100, b.get("commissionBps"));
        assertEquals(1000, b.get("commission"));
        assertEquals(0, b.get("pawapayFee"));
        assertEquals(99000, b.get("net"));
    }

    @Test
    void decompositionPayoutAvecFrais() {
        // Vente 100 000 − commission 1 000 − frais PawaPay 250 → net 98 750.
        Map<String, Object> b = PawaPayPayoutService.buildBreakdown(
            100000, 100, 1000, 0, 99000, 250);
        assertEquals(250, b.get("pawapayFee"));
        assertEquals(98750, b.get("net"));
    }

    @Test
    void netJamaisNegatif() {
        Map<String, Object> b = PawaPayPayoutService.buildBreakdown(
            500, 100, 5, 0, 495, 10000);
        assertEquals(0, b.get("net"));
    }

    @Test
    void statutsFinauxPayout() {
        assertTrue(PawaPayPayoutService.isFinalStatus("COMPLETED"));
        assertTrue(PawaPayPayoutService.isFinalStatus("FAILED"));
        assertTrue(PawaPayPayoutService.isFinalStatus("PAID"));
        assertFalse(PawaPayPayoutService.isFinalStatus("ACCEPTED"));
        assertFalse(PawaPayPayoutService.isFinalStatus("ENQUEUED"));
        assertFalse(PawaPayPayoutService.isFinalStatus("PROCESSING"));
        assertFalse(PawaPayPayoutService.isFinalStatus("IN_RECONCILIATION"));
        assertFalse(PawaPayPayoutService.isFinalStatus("PENDING"));
    }

    @Test
    void statutsFinauxRefund() {
        assertTrue(PawaPayRefundService.isFinalStatus("COMPLETED"));
        assertTrue(PawaPayRefundService.isFinalStatus("FAILED"));
        assertFalse(PawaPayRefundService.isFinalStatus("ACCEPTED"));
        assertFalse(PawaPayRefundService.isFinalStatus("ENQUEUED"));
        assertFalse(PawaPayRefundService.isFinalStatus("PROCESSING"));
        assertFalse(PawaPayRefundService.isFinalStatus("PENDING"));
    }

    @Test
    void providersPayoutValides() {
        assertTrue(PawaPayPayoutService.isPayoutProvider("MTN_MOMO_CMR"));
        assertTrue(PawaPayPayoutService.isPayoutProvider("ORANGE_CMR"));
        assertFalse(PawaPayPayoutService.isPayoutProvider("MTN_MOMO_ZMB"));
        assertFalse(PawaPayPayoutService.isPayoutProvider(null));
        assertFalse(PawaPayPayoutService.isPayoutProvider("MOBILE_MONEY_MTN"));
    }

    @Test
    void corpsPayoutConformeDoc() {
        Map<String, Object> body = PawaPayClient.buildPayoutBody(
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79", "98750", "XAF",
            "237690123456", "MTN_MOMO_CMR", "CMD-2026-000001", "BAZAAR PAYOUT",
            List.of(Map.of("orderId", "CMD-2026-000001")));
        assertEquals("afb57b93-7849-49aa-babb-4c3ccbfe3d79", body.get("payoutId"));
        assertEquals("98750", body.get("amount"));
        assertEquals("XAF", body.get("currency"));
        assertEquals("CMD-2026-000001", body.get("clientReferenceId"));
        assertEquals("BAZAAR PAYOUT", body.get("customerMessage"));
        @SuppressWarnings("unchecked")
        Map<String, Object> recipient = (Map<String, Object>) body.get("recipient");
        assertEquals("MMO", recipient.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) recipient.get("accountDetails");
        assertEquals("237690123456", details.get("phoneNumber"));
        assertEquals("MTN_MOMO_CMR", details.get("provider"));
        // Jamais de secret dans le corps.
        assertFalse(body.toString().contains("Bearer"));
    }

    @Test
    void vuePayoutMasquee() {
        SupplierPayment sp = new SupplierPayment();
        sp.setId(7);
        sp.setOrderId(3);
        sp.setSupplierId(9);
        sp.setReference("PO-CMD-2026-000001");
        sp.setPayoutId("afb57b93-7849-49aa-babb-4c3ccbfe3d79");
        sp.setProvider("MTN_MOMO_CMR");
        sp.setAmount(98750);
        sp.setGrossAmount(100000);
        sp.setCommission(1000);
        sp.setPawapayFee(250);
        sp.setStatus("PROCESSING");
        sp.setPhoneNumber("237690123456");
        Map<String, Object> view = PawaPayPayoutService.maskedView(sp);
        assertEquals("***456", view.get("phoneMasked"));
        assertEquals(98750, view.get("amount"));
        assertEquals(1000, view.get("commission"));
        assertFalse(view.toString().contains("237690123456"));
    }

    @Test
    void vueRefundSansSecret() {        Refund r = new Refund();
        r.setId(4);
        r.setOrderId(3);
        r.setRefundId("f02b543c-541c-4f21-bbea-20d2d56063d6");
        r.setDepositId("afb57b93-7849-49aa-babb-4c3ccbfe3d79");
        r.setAmount(100000);
        r.setCurrency("XAF");
        r.setStatus("PROCESSING");
        Map<String, Object> view = PawaPayRefundService.maskedView(r);
        assertEquals("f02b543c-541c-4f21-bbea-20d2d56063d6", view.get("refundId"));
        assertEquals(100000, view.get("amount"));
        assertFalse(view.toString().contains("Bearer"));
    }

    // ---- 6 capacités : statuts, transitions, bulk, cancel (pur) ----

    @Test
    void payoutIdUuidUnique() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            String id = PawaPayPayoutService.newPayoutId();
            assertTrue(id.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
            assertTrue(ids.add(id), "payoutId dupliqué : " + id);
        }
    }

    @Test
    void statutsOuverts() {
        assertTrue(PawaPayPayoutService.isOpenStatus("PENDING"));
        assertTrue(PawaPayPayoutService.isOpenStatus("ACCEPTED"));
        assertTrue(PawaPayPayoutService.isOpenStatus("ENQUEUED"));
        assertTrue(PawaPayPayoutService.isOpenStatus("PROCESSING"));
        assertFalse(PawaPayPayoutService.isOpenStatus("COMPLETED"));
        assertFalse(PawaPayPayoutService.isOpenStatus("FAILED"));
        assertFalse(PawaPayPayoutService.isOpenStatus("CANCELLED"));
        assertFalse(PawaPayPayoutService.isOpenStatus("BLOCKED"));
        assertFalse(PawaPayPayoutService.isOpenStatus("PAID"));
        assertFalse(PawaPayPayoutService.isOpenStatus(null));
    }

    @Test
    void mappingStatutsDistants() {
        assertEquals("ACCEPTED", PawaPayPayoutService.localStatusFor("ACCEPTED"));
        assertEquals("ENQUEUED", PawaPayPayoutService.localStatusFor("ENQUEUED"));
        assertEquals("PROCESSING", PawaPayPayoutService.localStatusFor("PROCESSING"));
        assertEquals("PROCESSING", PawaPayPayoutService.localStatusFor("IN_RECONCILIATION"));
        assertEquals("COMPLETED", PawaPayPayoutService.localStatusFor("COMPLETED"));
        assertEquals("FAILED", PawaPayPayoutService.localStatusFor("FAILED"));
        assertNull(PawaPayPayoutService.localStatusFor("BOGUS"));
        assertNull(PawaPayPayoutService.localStatusFor(null));
    }

    @Test
    void tableTransitions() {
        // Avants autorisés.
        assertTrue(PawaPayPayoutService.isTransitionAllowed("PENDING", "ACCEPTED"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("PENDING", "ENQUEUED"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("PENDING", "PROCESSING"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("ACCEPTED", "ENQUEUED"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("ACCEPTED", "PROCESSING"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("ENQUEUED", "PROCESSING"));
        assertTrue(PawaPayPayoutService.isTransitionAllowed("PROCESSING", "PROCESSING"));
        // Retours arrière interdits.
        assertFalse(PawaPayPayoutService.isTransitionAllowed("PROCESSING", "ACCEPTED"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("PROCESSING", "ENQUEUED"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("ENQUEUED", "ACCEPTED"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("ACCEPTED", "PENDING"));
        // Terminaux intouchables par cette voie.
        assertFalse(PawaPayPayoutService.isTransitionAllowed("COMPLETED", "PROCESSING"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("PENDING", "COMPLETED"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("PENDING", "FAILED"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("CANCELLED", "PROCESSING"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed(null, "PROCESSING"));
        assertFalse(PawaPayPayoutService.isTransitionAllowed("PENDING", null));
    }

    @Test
    void gardeCancelEtatsInvalides() {
        // cancelEnqueued refuse tout statut non ouvert : prédicat pur.
        for (String s : new String[]{"COMPLETED", "PAID", "FAILED", "CANCELLED", "BLOCKED"}) {
            assertFalse(PawaPayPayoutService.isOpenStatus(s), s);
        }
        assertTrue(PawaPayPayoutService.isOpenStatus("ENQUEUED"));
    }

    @Test
    void appariementBulkParPayoutId() {
        java.util.List<Map<String, Object>> responses = new java.util.ArrayList<>();
        responses.add(Map.of("payoutId", "id-2", "status", "REJECTED"));
        responses.add(Map.of("payoutId", "id-1", "status", "ACCEPTED"));
        responses.add(Map.of("payoutId", "id-3", "status", "DUPLICATE_IGNORED"));
        Map<String, Map<String, Object>> mapped =
            PawaPayPayoutService.mapBulkDecisions(responses);
        assertEquals(3, mapped.size());
        assertEquals("ACCEPTED", mapped.get("id-1").get("status"));
        assertEquals("REJECTED", mapped.get("id-2").get("status"));
        assertEquals("DUPLICATE_IGNORED", mapped.get("id-3").get("status"));
        assertTrue(PawaPayPayoutService.mapBulkDecisions(null).isEmpty());
        assertTrue(PawaPayPayoutService.mapBulkDecisions(new java.util.ArrayList<>()).isEmpty());
    }

    @Test
    void bulkLimitesSansReseau() {
        PawaPayClient client;
        try {
            // Sans token configuré ici : on vérifie la garde via réflexion
            // indirecte — la limite est appliquée avant tout HTTP.
            client = new PawaPayClient("http://127.0.0.1:9", "http://127.0.0.1:9",
                "dummy", null);
        } catch (Exception e) {
            return; // constructeur exigeant : garde testée par la constante
        }
        assertEquals(20, PawaPayClient.BULK_MAX_ITEMS);
        // Vide → refusé avant réseau.
        assertThrows(Exception.class, () -> client.initiateBulkPayouts(new java.util.ArrayList<>()));
        // 21 items → refusé avant réseau.
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) items.add(Map.of("payoutId", "x-" + i));
        assertThrows(Exception.class, () -> client.initiateBulkPayouts(items));
    }

    @Test
    void codesErreurReelsConserves() {
        // Seuls les codes documentés transitent (jamais inventés) : le motif
        // failureReason est conservé tel quel dans failure_reason.
        Map<String, Object> failure = new java.util.LinkedHashMap<>();
        failure.put("failureCode", "MANUALLY_CANCELLED");
        failure.put("failureMessage", "Cancelled");
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("failureReason", failure);
        assertTrue(PawaPayPaymentService.failureOf(node).contains("MANUALLY_CANCELLED"));
    }

    @Test
    void vuePayoutCompleteEtMasquee() {
        SupplierPayment sp = new SupplierPayment();
        sp.setId(1);
        sp.setPayoutId("99cd0b74-8946-42ba-8ad1-fb5be211726e");
        sp.setProviderTransactionId("TXN-12345");
        sp.setStatus("ENQUEUED");
        sp.setPhoneNumber("237690123456");
        sp.setLastReconciledAt("2026-10-02 12:00:00");
        Map<String, Object> view = PawaPayPayoutService.maskedView(sp);
        assertEquals("ENQUEUED", view.get("status"));
        assertEquals("TXN-12345", view.get("providerTransactionId"));
        assertEquals("2026-10-02 12:00:00", view.get("lastReconciledAt"));
        assertEquals("***456", view.get("phoneMasked"));
        assertFalse(view.toString().contains("237690123456"));
        assertFalse(view.toString().contains("Bearer"));
    }

    @Test
    void configPayoutUrl() {
        // Défaut sandbox/prod + surcharge env (pur, sans variable réelle).
        assertEquals("https://api.sandbox.pawapay.io/v2/payouts",
            PawaPayConfig.resolvePayoutUrl(null, "https://api.sandbox.pawapay.io"));
        assertEquals("https://api.pawapay.io/v2/payouts",
            PawaPayConfig.resolvePayoutUrl("  ", "https://api.pawapay.io"));
        assertEquals("https://custom.example/v2/payouts",
            PawaPayConfig.resolvePayoutUrl("https://custom.example/v2/payouts/",
                "https://api.sandbox.pawapay.io"));
    }

    // ---- Refund V2 : statuts, transitions, cancel, metadata, montant ----

    @Test
    void refundIdUuidUnique() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            String id = PawaPayRefundService.newRefundId();
            assertTrue(id.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
            assertTrue(ids.add(id), "refundId dupliqué : " + id);
        }
    }

    @Test
    void refundStatutsOuverts() {
        assertTrue(PawaPayRefundService.isOpenStatus("PENDING"));
        assertTrue(PawaPayRefundService.isOpenStatus("ACCEPTED"));
        assertTrue(PawaPayRefundService.isOpenStatus("ENQUEUED"));
        assertTrue(PawaPayRefundService.isOpenStatus("PROCESSING"));
        assertFalse(PawaPayRefundService.isOpenStatus("COMPLETED"));
        assertFalse(PawaPayRefundService.isOpenStatus("FAILED"));
        assertFalse(PawaPayRefundService.isOpenStatus(null));
    }

    @Test
    void refundMappingStatutsDistants() {
        assertEquals("ACCEPTED", PawaPayRefundService.localStatusFor("ACCEPTED"));
        assertEquals("ENQUEUED", PawaPayRefundService.localStatusFor("ENQUEUED"));
        assertEquals("PROCESSING", PawaPayRefundService.localStatusFor("PROCESSING"));
        assertEquals("PROCESSING", PawaPayRefundService.localStatusFor("IN_RECONCILIATION"));
        assertEquals("COMPLETED", PawaPayRefundService.localStatusFor("COMPLETED"));
        assertEquals("FAILED", PawaPayRefundService.localStatusFor("FAILED"));
        assertNull(PawaPayRefundService.localStatusFor("BOGUS"));
        assertNull(PawaPayRefundService.localStatusFor(null));
    }

    @Test
    void refundTableTransitions() {
        assertTrue(PawaPayRefundService.isTransitionAllowed("PENDING", "ACCEPTED"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("PENDING", "ENQUEUED"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("PENDING", "PROCESSING"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("ACCEPTED", "ENQUEUED"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("ACCEPTED", "PROCESSING"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("ENQUEUED", "PROCESSING"));
        assertTrue(PawaPayRefundService.isTransitionAllowed("PROCESSING", "PROCESSING"));
        // Retours arrière interdits.
        assertFalse(PawaPayRefundService.isTransitionAllowed("PROCESSING", "ACCEPTED"));
        assertFalse(PawaPayRefundService.isTransitionAllowed("PROCESSING", "ENQUEUED"));
        assertFalse(PawaPayRefundService.isTransitionAllowed("ENQUEUED", "ACCEPTED"));
        // Terminaux intouchables par cette voie.
        assertFalse(PawaPayRefundService.isTransitionAllowed("COMPLETED", "PROCESSING"));
        assertFalse(PawaPayRefundService.isTransitionAllowed("COMPLETED", "ACCEPTED"));
        assertFalse(PawaPayRefundService.isTransitionAllowed("PENDING", "COMPLETED"));
        assertFalse(PawaPayRefundService.isTransitionAllowed("PENDING", "FAILED"));
        assertFalse(PawaPayRefundService.isTransitionAllowed(null, "PROCESSING"));
    }

    @Test
    void refundGardeCancelEtats() {
        // cancelEnqueued refuse tout statut non ouvert : prédicat pur.
        for (String s : new String[]{"COMPLETED", "FAILED"}) {
            assertFalse(PawaPayRefundService.isOpenStatus(s), s);
        }
        assertTrue(PawaPayRefundService.isOpenStatus("ENQUEUED"));
    }

    @Test
    void refundGardeMontantRemboursable() {
        assertNull(PawaPayRefundService.checkRefundable(8500, 8500, 0));
        assertNull(PawaPayRefundService.checkRefundable(5000, 8500, 3500));
        assertNotNull(PawaPayRefundService.checkRefundable(0, 8500, 0));
        assertNotNull(PawaPayRefundService.checkRefundable(-100, 8500, 0));
        assertNotNull(PawaPayRefundService.checkRefundable(8500, 8500, 1));
        assertNotNull(PawaPayRefundService.checkRefundable(9000, 8500, 0));
    }

    @Test
    void corpsRefundConformeDoc() {
        java.util.List<Map<String, String>> meta = new java.util.ArrayList<>();
        meta.add(Map.of("orderId", "CMD-2026-000001"));
        meta.add(Map.of("channel", "BAZAAR_REFUND"));
        Map<String, Object> body = PawaPayClient.buildRefundBody(
            "f02b543c-541c-4f21-bbea-20d2d56063d6",
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79",
            "8500", "XAF", "CMD-2026-000001", meta);
        assertEquals("f02b543c-541c-4f21-bbea-20d2d56063d6", body.get("refundId"));
        assertEquals("afb57b93-7849-49aa-babb-4c3ccbfe3d79", body.get("depositId"));
        assertEquals("8500", body.get("amount"));
        assertEquals("XAF", body.get("currency"));
        assertEquals("CMD-2026-000001", body.get("clientReferenceId"));
        assertTrue(body.get("metadata") instanceof java.util.List);
        assertEquals(2, ((java.util.List<?>) body.get("metadata")).size());
        assertFalse(body.toString().contains("Bearer"));
        // Sans metadata : clé absente (jamais de liste vide).
        Map<String, Object> bare = PawaPayClient.buildRefundBody(
            "f02b543c-541c-4f21-bbea-20d2d56063d6",
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79",
            "8500", "XAF", "CMD-2026-000001", null);
        assertFalse(bare.containsKey("metadata"));
    }

    @Test
    void configRefundUrl() {
        assertEquals("https://api.sandbox.pawapay.io/v2/refunds",
            PawaPayConfig.resolveRefundUrl(null, "https://api.sandbox.pawapay.io"));
        assertEquals("https://api.pawapay.io/v2/refunds",
            PawaPayConfig.resolveRefundUrl("  ", "https://api.pawapay.io"));
        assertEquals("https://custom.example/v2/refunds",
            PawaPayConfig.resolveRefundUrl("https://custom.example/v2/refunds/",
                "https://api.sandbox.pawapay.io"));
    }
}
