package com.ecommerce.pawapay;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Config PawaPay : aucune DB, aucun réseau, aucun token requis.
 * Ne vérifie jamais la valeur du token (présence uniquement).
 */
class PawaPayConfigTest {

    @Test
    void mappingMethodesVersProvidersCmr() {
        assertEquals("MTN_MOMO_CMR", PawaPayConfig.toPawaPayProvider("MOBILE_MONEY_MTN"));
        assertEquals("ORANGE_CMR", PawaPayConfig.toPawaPayProvider("MOBILE_MONEY_ORANGE"));
        assertNull(PawaPayConfig.toPawaPayProvider("PAIEMENT_LIVRAISON"));
        assertNull(PawaPayConfig.toPawaPayProvider("CART_BANCAIRE"));
        assertNull(PawaPayConfig.toPawaPayProvider(null));
    }

    @Test
    void resumeStatutSansSecret() {
        Map<String, Object> summary = PawaPayConfig.statusSummary();
        assertTrue(summary.containsKey("configured"));
        assertTrue(summary.containsKey("environment"));
        assertTrue(summary.containsKey("apiUrl"));
        Object token = summary.get("token");
        assertTrue("PRESENT".equals(token) || "MISSING".equals(token),
            "le résumé ne doit contenir que PRESENT/MISSING, jamais la valeur");
        String real = System.getenv(PawaPayConfig.ENV_TOKEN);
        if (real != null && !real.trim().isEmpty()) {
            assertNotEquals(real, String.valueOf(token));
        }
        assertEquals("XAF", summary.get("currency"));
        assertEquals("CMR", summary.get("country"));
    }

    @Test
    void urlsOfficielles() {
        assertEquals("https://api.sandbox.pawapay.io", PawaPayConfig.SANDBOX_BASE_URL);
        assertEquals("https://api.pawapay.io", PawaPayConfig.PRODUCTION_BASE_URL);
    }

    @Test
    void resolutionUrlsParVariable() {
        String sandbox = "https://api.sandbox.pawapay.io";
        String custom = "https://mock.local:9/api/";
        // Absente → fallback base + chemin doc.
        assertEquals(sandbox + "/v2/wallet-balances",
            PawaPayConfig.resolveSuffixedUrl(null, sandbox, PawaPayConfig.WALLET_BALANCES_PATH));
        assertEquals(sandbox + "/v2/statements",
            PawaPayConfig.resolveSuffixedUrl("  ", sandbox, PawaPayConfig.STATEMENT_PATH));
        assertEquals(sandbox + "/v2/active-conf",
            PawaPayConfig.resolveSuffixedUrl(null, sandbox, PawaPayConfig.ACTIVE_CONFIGURATION_PATH));
        assertEquals(sandbox + "/v2/availability",
            PawaPayConfig.resolveSuffixedUrl(null, sandbox, PawaPayConfig.AVAILABILITY_PATH));
        assertEquals(sandbox + "/v2/predict-provider",
            PawaPayConfig.resolveSuffixedUrl(null, sandbox, PawaPayConfig.PREDICT_PROVIDER_PATH));
        // Chemin doc exact clés publiques : /v2/public-key/http (pas /v2/public-keys).
        assertEquals(sandbox + "/v2/public-key/http",
            PawaPayConfig.resolveSuffixedUrl(null, sandbox, PawaPayConfig.PUBLIC_KEYS_PATH));
        // Présente → valeur configurée (slash final neutralisé).
        assertEquals("https://mock.local:9/api/v2/wallet-balances",
            PawaPayConfig.resolveSuffixedUrl(custom + "v2/wallet-balances/", sandbox,
                PawaPayConfig.WALLET_BALANCES_PATH));
        // Base custom + chemins par défaut.
        assertEquals(custom + "v2/deposits",
            PawaPayConfig.resolveDepositUrl(null, custom));
        assertEquals(custom + "v2/payouts",
            PawaPayConfig.resolvePayoutUrl("", custom));
        assertEquals(custom + "v2/refunds",
            PawaPayConfig.resolveRefundUrl(null, custom));
    }

    @Test
    void resumeStatutContientToutesUrlsSansSecret() {
        Map<String, Object> summary = PawaPayConfig.statusSummary();
        for (String key : new String[]{"apiUrl", "depositUrl", "payoutUrl", "refundUrl",
                "walletBalancesUrl", "statementUrl", "activeConfigurationUrl",
                "availabilityUrl", "predictProviderUrl", "publicKeysUrl"}) {
            assertTrue(summary.containsKey(key), "clé manquante : " + key);
            Object v = summary.get(key);
            assertTrue(v instanceof String && !((String) v).isEmpty(), "URL vide : " + key);
        }
        Object token = summary.get("token");
        assertTrue("PRESENT".equals(token) || "MISSING".equals(token));
        String real = System.getenv(PawaPayConfig.ENV_TOKEN);
        if (real != null && !real.trim().isEmpty()) {
            assertNotEquals(real, String.valueOf(token));
            for (Object v : summary.values()) {
                assertFalse(real.equals(String.valueOf(v)), "token exposé dans le résumé");
            }
        }
    }

    @Test
    void providerPawaPay() {
        assertEquals("PAWAPAY", PawaPayProvider.instance().getId());
        assertTrue(PawaPayProvider.instance().supportsMethod("MOBILE_MONEY_MTN"));
        assertTrue(PawaPayProvider.instance().supportsMethod("MOBILE_MONEY_ORANGE"));
        assertFalse(PawaPayProvider.instance().supportsMethod("PAIEMENT_LIVRAISON"));
        assertTrue(PawaPayProvider.instance().autoConfirmable());
    }
}
