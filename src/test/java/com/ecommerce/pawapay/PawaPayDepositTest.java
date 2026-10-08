package com.ecommerce.pawapay;

import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Module Deposit PawaPay Sandbox : les 10 cas automatisables sans DB,
 * sans réseau et sans token réel (les cas DB/réseau réel sont couverts
 * par la procédure sandbox manuelle documentée).
 */
class PawaPayDepositTest {

    // Test 1 — configuration présente (résolution sandbox par défaut).
    @Test
    void configurationSandboxParDefaut() {
        assertEquals(PawaPayConfig.SANDBOX_BASE_URL,
            PawaPayConfig.resolveApiBaseUrl(null, "sandbox"));
        assertEquals(PawaPayConfig.SANDBOX_BASE_URL + "/v2/deposits",
            PawaPayConfig.resolveDepositUrl(null, PawaPayConfig.SANDBOX_BASE_URL));
        assertEquals("https://api.sandbox.pawapay.io/v2/deposits",
            PawaPayConfig.resolveDepositUrl(null, "https://api.sandbox.pawapay.io/"));
        assertEquals("https://mock:9999/v2/deposits",
            PawaPayConfig.resolveDepositUrl("https://mock:9999/v2/deposits", "https://api.sandbox.pawapay.io"));
    }

    // Test 2 — token absent : erreur claire, sans valeur exposée.
    @Test
    void tokenAbsentErreurClaire() {
        for (String missing : new String[]{null, "", "   "}) {
            AppException e = assertThrows(AppException.class,
                () -> PawaPayConfig.checkToken(missing));
            assertEquals(ErrorCodes.PROVIDER_UNAVAILABLE, e.getCode());
            assertTrue(e.getMessage().contains("PAWAPAY_API_TOKEN"));
        }
        assertDoesNotThrow(() -> PawaPayConfig.checkToken("dummy"));
    }

    // Test 3 — URL absente : repli documenté vers base + /v2/deposits.
    @Test
    void urlAbsenteRepliDocumente() {
        assertEquals("https://api.sandbox.pawapay.io/v2/deposits",
            PawaPayConfig.resolveDepositUrl("", PawaPayConfig.SANDBOX_BASE_URL));
        assertEquals("https://api.pawapay.io/v2/deposits",
            PawaPayConfig.resolveDepositUrl(null, PawaPayConfig.PRODUCTION_BASE_URL));
    }

    // Test 4 — depositId unique au format UUIDv4.
    @Test
    void depositIdUniqueEtFormatUuid() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String id = PawaPayPaymentService.newDepositId();
            assertTrue(id.matches("[0-9a-fA-F-]{36}"), id);
            assertTrue(ids.add(id), "depositId réutilisé : " + id);
        }
    }

    // Test 5 — JSON Deposit conforme (payer MMO, metadata en LISTE).
    @Test
    void jsonDepositConformeDocumentation() {
        List<Map<String, String>> metadata = new ArrayList<>();
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("orderId", "CMD-2026-000123");
        metadata.add(m1);
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("channel", "BAZAAR_WEB");
        metadata.add(m2);
        Map<String, Object> body = PawaPayClient.buildDepositBody(
            "f4401bd2-1568-4140-bf2d-eb77d2b2b639", "525000", "XAF",
            "237690123456", "MTN_MOMO_CMR", "CMD-2026-000123", "BAZAAR PAYMENT", metadata);
        assertEquals("f4401bd2-1568-4140-bf2d-eb77d2b2b639", body.get("depositId"));
        assertEquals("525000", body.get("amount"));
        assertTrue(body.get("amount") instanceof String, "amount doit être une chaîne (doc PawaPay)");
        assertEquals("XAF", body.get("currency"));
        assertEquals("CMD-2026-000123", body.get("clientReferenceId"));
        assertEquals("BAZAAR PAYMENT", body.get("customerMessage"));
        assertTrue("BAZAAR PAYMENT".matches("^[a-zA-Z0-9 ]+$"));
        assertTrue("BAZAAR PAYMENT".length() >= 4 && "BAZAAR PAYMENT".length() <= 22);
        @SuppressWarnings("unchecked")
        Map<String, Object> payer = (Map<String, Object>) body.get("payer");
        assertEquals("MMO", payer.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) payer.get("accountDetails");
        assertEquals("237690123456", details.get("phoneNumber"));
        assertEquals("MTN_MOMO_CMR", details.get("provider"));
        assertTrue(body.get("metadata") instanceof List, "metadata doit être une liste (doc PawaPay)");
        assertFalse(com.ecommerce.util.JsonUtil.toJson(body).contains("Bearer"));
    }

    // Test 6 — montant frontend falsifié : toujours ignoré.
    @Test
    void montantClientIgnore() {
        assertEquals(525000, PawaPayDepositService.effectiveAmount(525000, 1));
        assertEquals(500000, PawaPayDepositService.effectiveAmount(500000, 999999999));
        assertEquals(500000, PawaPayDepositService.effectiveAmount(500000, "0"));
        assertEquals(500000, PawaPayDepositService.effectiveAmount(500000, null));
    }

    // Test 7 — commande déjà payée : statuts soldés détectés.
    @Test
    void commandeDejaPayeeDetectee() {
        assertTrue(PawaPayDepositService.isPaidStatus("COMPLETED"));
        assertTrue(PawaPayDepositService.isPaidStatus("REUSSIE"));
        assertFalse(PawaPayDepositService.isPaidStatus("PROCESSING"));
        assertFalse(PawaPayDepositService.isPaidStatus("PENDING"));
        assertFalse(PawaPayDepositService.isPaidStatus("FAILED"));
        assertFalse(PawaPayDepositService.isPaidStatus("EN_ATTENTE"));
        assertFalse(PawaPayDepositService.isPaidStatus(null));
    }

    // Disponibilité providers : parsing défensif de l'active-conf.
    @Test
    void disponibiliteProvidersDepuisActiveConf() {
        Map<String, Object> depOp = new LinkedHashMap<>();
        depOp.put("DEPOSIT", new LinkedHashMap<>());
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("currency", "XAF");
        cur.put("operationTypes", depOp);
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("provider", "MTN_MOMO_CMR");
        java.util.List<Object> currencies = new java.util.ArrayList<>();
        currencies.add(cur);
        prov.put("currencies", currencies);
        Map<String, Object> country = new LinkedHashMap<>();
        java.util.List<Object> providers = new java.util.ArrayList<>();
        providers.add(prov);
        country.put("providers", providers);
        Map<String, Object> conf = new LinkedHashMap<>();
        java.util.List<Object> countries = new java.util.ArrayList<>();
        countries.add(country);
        conf.put("countries", countries);

        java.util.Set<String> live = PawaPayClient.parseDepositProviders(conf);
        assertTrue(live.contains("MTN_MOMO_CMR"));
        assertFalse(live.contains("ORANGE_CMR"));

        assertTrue(PawaPayClient.parseDepositProviders(null).isEmpty());
        assertTrue(PawaPayClient.parseDepositProviders(new LinkedHashMap<>()).isEmpty());
    }

    // Codes d'erreur du module Deposit (stables, sans secret).
    @Test
    void codesErreurDeposit() {
        assertEquals("ORDER_ALREADY_PAID",
            com.ecommerce.util.ErrorCodes.ORDER_ALREADY_PAID);
        assertEquals("PAWAPAY_TIMEOUT",
            com.ecommerce.util.ErrorCodes.PAWAPAY_TIMEOUT);
        assertEquals("DUPLICATE_DEPOSIT",
            com.ecommerce.util.ErrorCodes.DUPLICATE_DEPOSIT);
        assertEquals("PAYMENT_ALREADY_PROCESSING",
            com.ecommerce.util.ErrorCodes.PAYMENT_ALREADY_PROCESSING);
        assertEquals("PAWAPAY_REJECTED",
            com.ecommerce.util.ErrorCodes.PAWAPAY_REJECTED);
    }

    // Tests 8/9 — clé d'idempotence du callback : stable et discriminante.
    @Test
    void cleIdempotenceCallback() {
        String id = "f4401bd2-1568-4140-bf2d-eb77d2b2b639";
        assertEquals(id + ":COMPLETED", PawaPayDepositService.depositEventKey(id, "COMPLETED"));
        assertNotEquals(PawaPayDepositService.depositEventKey(id, "COMPLETED"),
            PawaPayDepositService.depositEventKey(id, "FAILED"));
        // Seuls les statuts finaux font transition (garde anti double-traitement).
        assertTrue(PawaPayPaymentService.isFinalStatus("COMPLETED"));
        assertFalse(PawaPayPaymentService.isFinalStatus("PROCESSING"));
    }
}
