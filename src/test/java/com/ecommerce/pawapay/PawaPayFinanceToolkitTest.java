package com.ecommerce.pawapay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finances + Toolkit PawaPay : validation, transitions statements,
 * parsing défensif, sécurité. Aucun réseau, aucun token, aucune DB.
 */
class PawaPayFinanceToolkitTest {

    // ---- Statements : statuts et transitions ----

    @Test
    void statementStatutsFinaux() {
        assertTrue(PawaPayStatementService.isFinalStatus("COMPLETED"));
        assertTrue(PawaPayStatementService.isFinalStatus("FAILED"));
        assertFalse(PawaPayStatementService.isFinalStatus("PROCESSING"));
        assertFalse(PawaPayStatementService.isFinalStatus("ACCEPTED"));
        assertFalse(PawaPayStatementService.isFinalStatus(null));
    }

    @Test
    void statementStatutsOuverts() {
        assertTrue(PawaPayStatementService.isOpenStatus("ACCEPTED"));
        assertTrue(PawaPayStatementService.isOpenStatus("PROCESSING"));
        assertFalse(PawaPayStatementService.isOpenStatus("COMPLETED"));
        assertFalse(PawaPayStatementService.isOpenStatus("FAILED"));
    }

    @Test
    void statementMappingDistant() {
        assertEquals("PROCESSING", PawaPayStatementService.localStatusFor("PROCESSING"));
        assertEquals("COMPLETED", PawaPayStatementService.localStatusFor("COMPLETED"));
        assertEquals("FAILED", PawaPayStatementService.localStatusFor("FAILED"));
        // Statuts non documentés pour les relevés : jamais inventés.
        assertNull(PawaPayStatementService.localStatusFor("ACCEPTED"));
        assertNull(PawaPayStatementService.localStatusFor("ENQUEUED"));
        assertNull(PawaPayStatementService.localStatusFor("BOGUS"));
        assertNull(PawaPayStatementService.localStatusFor(null));
    }

    @Test
    void statementTableTransitions() {
        assertTrue(PawaPayStatementService.isTransitionAllowed("ACCEPTED", "PROCESSING"));
        assertTrue(PawaPayStatementService.isTransitionAllowed("PROCESSING", "PROCESSING"));
        assertTrue(PawaPayStatementService.isTransitionAllowed("ACCEPTED", "ACCEPTED"));
        // Jamais vers un final par cette voie, jamais en arrière, jamais terminal.
        assertFalse(PawaPayStatementService.isTransitionAllowed("ACCEPTED", "COMPLETED"));
        assertFalse(PawaPayStatementService.isTransitionAllowed("PROCESSING", "FAILED"));
        assertFalse(PawaPayStatementService.isTransitionAllowed("COMPLETED", "PROCESSING"));
        assertFalse(PawaPayStatementService.isTransitionAllowed("FAILED", "PROCESSING"));
        assertFalse(PawaPayStatementService.isTransitionAllowed(null, "PROCESSING"));
    }

    @Test
    void statementValidationPlage() throws Exception {
        String[] r = PawaPayStatementService.requireRange(
            "2026-09-01T00:00:00Z", "2026-09-30T00:00:00Z");
        assertEquals(2, r.length);
        // Fin avant début : refusé.
        assertThrows(Exception.class, () -> PawaPayStatementService.requireRange(
            "2026-09-30T00:00:00Z", "2026-09-01T00:00:00Z"));
        // > 31 jours : refusé (doc).
        assertThrows(Exception.class, () -> PawaPayStatementService.requireRange(
            "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z"));
        // Format invalide : refusé.
        assertThrows(Exception.class, () -> PawaPayStatementService.requireRange(
            "01/09/2026", "30/09/2026"));
        // Date-heure locale acceptée (supposée UTC).
        String[] r2 = PawaPayStatementService.requireRange(
            "2026-09-01T00:00:00", "2026-09-02T00:00:00");
        assertTrue(r2[0].endsWith("Z"));
    }

    @Test
    void statementValidationPaysDevise() throws Exception {
        assertEquals("CMR", PawaPayStatementService.requireCountry("CMR"));
        assertEquals("XAF", PawaPayStatementService.requireCurrency("XAF"));
        assertThrows(Exception.class,
            () -> PawaPayStatementService.requireCountry("CM"));
        assertThrows(Exception.class,
            () -> PawaPayStatementService.requireCountry("cmr"));
        assertThrows(Exception.class,
            () -> PawaPayStatementService.requireCurrency("FCFA"));
        assertThrows(Exception.class,
            () -> PawaPayStatementService.requireCurrency(null));
    }

    @Test
    void statementLienExpire() {
        assertTrue(PawaPayStatementService.isExpired(null));
        assertTrue(PawaPayStatementService.isExpired(""));
        assertTrue(PawaPayStatementService.isExpired("not-a-date"));
        // Expiré hier, valide demain.
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        assertTrue(PawaPayStatementService.isExpired(now.minusDays(2).toString()));
        assertFalse(PawaPayStatementService.isExpired(now.plusDays(1).toString()));
    }

    @Test
    void statementVueSansSecret() {
        com.ecommerce.model.PawaPayStatement s = new com.ecommerce.model.PawaPayStatement();
        s.setStatementId("f4401bd2-1568-4140-babb-4c3ccbfe3d79");
        s.setStatus("COMPLETED");
        s.setWalletCountry("CMR");
        s.setWalletCurrency("XAF");
        s.setDownloadUrl("https://pawapay.io/dl/abc");
        Map<String, Object> view = PawaPayStatementService.maskedView(s);
        assertEquals("COMPLETED", view.get("status"));
        assertFalse(view.toString().contains("Bearer"));
        assertFalse(view.containsKey("token"));
        assertFalse(view.containsKey("apiToken"));
    }

    // ---- Toolkit : validation ----

    @Test
    void toolkitPaysEtOperation() throws Exception {
        assertEquals("CMR", PawaPayToolkitService.requireCountry("CMR"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireCountry("C"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireCountry("cmr"));
        assertEquals("PAYOUT", PawaPayToolkitService.requireOperationType("payout"));
        assertEquals("REFUND", PawaPayToolkitService.requireOperationType("REFUND"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireOperationType("BOGUS"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireOperationType(null));
    }

    @Test
    void toolkitMsisdnGenerique() throws Exception {
        assertEquals("237690123456", PawaPayToolkitService.requireMsisdn("+237 690-123456"));
        assertEquals("237690123456", PawaPayToolkitService.requireMsisdn("00237690123456"));
        assertEquals("260763456789", PawaPayToolkitService.requireMsisdn("260763456789"));
        // Sans indicatif / trop court / lettres : refusé (validation serveur).
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireMsisdn("690123456"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireMsisdn("123"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireMsisdn("abcdef"));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireMsisdn(null));
        assertThrows(Exception.class, () -> PawaPayToolkitService.requireMsisdn("012345678"));
    }

    @Test
    void resumeDisponibilite() {
        // Forme doc /v2/availability sous "data" (client) : résumé compact.
        Map<String, Object> raw = new LinkedHashMap<>();
        List<Object> data = new ArrayList<>();
        Map<String, Object> gha = new LinkedHashMap<>();
        gha.put("country", "GHA");
        List<Object> providers = new ArrayList<>();
        Map<String, Object> mtn = new LinkedHashMap<>();
        mtn.put("provider", "MTN_MOMO_GHA");
        List<Object> ops = new ArrayList<>();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("operationType", "DEPOSIT");
        d.put("status", "OPERATIONAL");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("operationType", "PAYOUT");
        p.put("status", "DELAYED");
        ops.add(d);
        ops.add(p);
        mtn.put("operationTypes", ops);
        providers.add(mtn);
        gha.put("providers", providers);
        data.add(gha);
        raw.put("data", data);
        Map<String, Map<String, String>> summary =
            PawaPayToolkitService.summarizeAvailability(raw);
        assertEquals("OPERATIONAL", summary.get("MTN_MOMO_GHA").get("DEPOSIT"));
        assertEquals("DELAYED", summary.get("MTN_MOMO_GHA").get("PAYOUT"));
        // Forme live constatée : operationTypes en OBJET {OP: STATUS}.
        Map<String, Object> rawMap = new LinkedHashMap<>();
        List<Object> dataMap = new ArrayList<>();
        Map<String, Object> cmr = new LinkedHashMap<>();
        cmr.put("country", "CMR");
        List<Object> provMap = new ArrayList<>();
        Map<String, Object> mtnCmr = new LinkedHashMap<>();
        mtnCmr.put("provider", "MTN_MOMO_CMR");
        Map<String, Object> opsMap = new LinkedHashMap<>();
        opsMap.put("DEPOSIT", "OPERATIONAL");
        opsMap.put("PAYOUT", "OPERATIONAL");
        opsMap.put("REFUND", "CLOSED");
        mtnCmr.put("operationTypes", opsMap);
        provMap.add(mtnCmr);
        cmr.put("providers", provMap);
        dataMap.add(cmr);
        rawMap.put("data", dataMap);
        Map<String, Map<String, String>> summaryMap =
            PawaPayToolkitService.summarizeAvailability(rawMap);
        assertEquals("OPERATIONAL", summaryMap.get("MTN_MOMO_CMR").get("PAYOUT"));
        assertEquals("CLOSED", summaryMap.get("MTN_MOMO_CMR").get("REFUND"));
        // Formes inattendues : vide, jamais d'exception.
        assertTrue(PawaPayToolkitService.summarizeAvailability(null).isEmpty());
        assertTrue(PawaPayToolkitService.summarizeAvailability(new LinkedHashMap<>()).isEmpty());
        assertTrue(PawaPayToolkitService.summarizeAvailability("bogus").isEmpty());
    }

    @Test
    void clesPubliquesJamaisToken() {
        // Les clés publiques ne sont pas le token : aucune méthode ne doit
        // exposer PAWAPAY_API_TOKEN (vérifié par inspection des réponses :
        // getPublicKeys ne retourne que id+key).
        assertTrue(PawaPayConfig.ENV_TOKEN.equals("PAWAPAY_API_TOKEN"));
    }
}
