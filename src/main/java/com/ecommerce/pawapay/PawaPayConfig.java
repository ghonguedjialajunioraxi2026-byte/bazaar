package com.ecommerce.pawapay;

import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration centralisée PawaPay (Sandbox d'abord).
 *
 * Source de vérité : variables d'environnement (même système que
 * DB_URL/DB_USER/DB_PASSWORD, voir .env.example). Le token n'est JAMAIS
 * écrit en dur, ni loggé, ni exposé au frontend, ni stocké en base.
 *
 * URLs vérifiées dans la documentation officielle
 * (https://docs.pawapay.io/, Merchant API v2) :
 * - Sandbox    : https://api.sandbox.pawapay.io
 * - Production : https://api.pawapay.io
 */
public final class PawaPayConfig {

    public static final String ENV_TOKEN = "PAWAPAY_API_TOKEN";
    public static final String ENV_API_URL = "PAWAPAY_API_URL";
    public static final String ENV_DEPOSIT_URL = "PAWAPAY_DEPOSIT_URL";
    public static final String ENV_PAYOUT_URL = "PAWAPAY_PAYOUT_URL";
    public static final String ENV_REFUND_URL = "PAWAPAY_REFUND_URL";
    public static final String ENV_WALLET_BALANCES_URL = "PAWAPAY_WALLET_BALANCES_URL";
    public static final String ENV_STATEMENT_URL = "PAWAPAY_STATEMENT_URL";
    public static final String ENV_ACTIVE_CONFIGURATION_URL = "PAWAPAY_ACTIVE_CONFIGURATION_URL";
    public static final String ENV_AVAILABILITY_URL = "PAWAPAY_AVAILABILITY_URL";
    public static final String ENV_PREDICT_PROVIDER_URL = "PAWAPAY_PREDICT_PROVIDER_URL";
    public static final String ENV_PUBLIC_KEYS_URL = "PAWAPAY_PUBLIC_KEYS_URL";
    public static final String ENV_ENVIRONMENT = "PAWAPAY_ENVIRONMENT";

    public static final String SANDBOX_BASE_URL = "https://api.sandbox.pawapay.io";
    public static final String PRODUCTION_BASE_URL = "https://api.pawapay.io";
    public static final String DEPOSIT_PATH = "/v2/deposits";
    public static final String PAYOUT_PATH = "/v2/payouts";
    public static final String REFUND_PATH = "/v2/refunds";
    public static final String WALLET_BALANCES_PATH = "/v2/wallet-balances";
    public static final String STATEMENT_PATH = "/v2/statements";
    public static final String ACTIVE_CONFIGURATION_PATH = "/v2/active-conf";
    public static final String AVAILABILITY_PATH = "/v2/availability";
    public static final String PREDICT_PROVIDER_PATH = "/v2/predict-provider";
    /** Doc exacte : /v2/public-key/http (singulier + suffixe, pas /v2/public-keys). */
    public static final String PUBLIC_KEYS_PATH = "/v2/public-key/http";

    /** Devise imposée par MBOA STYLE (XAF, sans décimales côté MTN/Orange CMR). */
    public static final String CURRENCY = "XAF";
    public static final String COUNTRY = "CMR";

    /** Identifiant provider stocké en base (colonne payments.provider). */
    public static final String PROVIDER_ID = "PAWAPAY";

    private PawaPayConfig() {}

    /** Environnement demandé : "sandbox" par défaut (jamais de prod auto). */
    public static String getEnvironment() {
        return resolveEnvironment(System.getenv(ENV_ENVIRONMENT));
    }

    static String resolveEnvironment(String configured) {
        if (configured == null || configured.trim().isEmpty()) return "sandbox";
        return configured.trim().toLowerCase();
    }

    public static boolean isSandbox() {
        return !"production".equals(getEnvironment());
    }

    /**
     * Base URL de l'API. PAWAPAY_API_URL prime si définie (utile pour les
     * tests contre un mock local), sinon déduite de l'environnement.
     */
    public static String getApiBaseUrl() {
        return resolveApiBaseUrl(System.getenv(ENV_API_URL), getEnvironment());
    }

    static String resolveApiBaseUrl(String configured, String environment) {
        if (configured != null && !configured.trim().isEmpty()) {
            return stripTrailingSlash(configured.trim());
        }
        return "production".equals(environment) ? PRODUCTION_BASE_URL : SANDBOX_BASE_URL;
    }

    /**
     * URL complète du Deposit (POST). AUCUNE URL en dur dans le code
     * métier : PAWAPAY_DEPOSIT_URL prime ; à défaut, base + /v2/deposits.
     */
    public static String getDepositUrl() {
        return resolveDepositUrl(System.getenv(ENV_DEPOSIT_URL), getApiBaseUrl());
    }

    static String resolveDepositUrl(String configured, String baseUrl) {
        if (configured != null && !configured.trim().isEmpty()) {
            return stripTrailingSlash(configured.trim());
        }
        return stripTrailingSlash(baseUrl) + DEPOSIT_PATH;
    }

    /**
     * URL complète du Payout (POST). AUCUNE URL en dur dans le code
     * métier : PAWAPAY_PAYOUT_URL prime ; à défaut, base + /v2/payouts.
     */
    public static String getPayoutUrl() {
        return resolvePayoutUrl(System.getenv(ENV_PAYOUT_URL), getApiBaseUrl());
    }

    static String resolvePayoutUrl(String configured, String baseUrl) {
        if (configured != null && !configured.trim().isEmpty()) {
            return stripTrailingSlash(configured.trim());
        }
        return stripTrailingSlash(baseUrl) + PAYOUT_PATH;
    }

    /**
     * URL complète du Refund (POST). AUCUNE URL en dur dans le code
     * métier : PAWAPAY_REFUND_URL prime ; à défaut, base + /v2/refunds.
     */
    public static String getRefundUrl() {
        return resolveRefundUrl(System.getenv(ENV_REFUND_URL), getApiBaseUrl());
    }

    static String resolveRefundUrl(String configured, String baseUrl) {
        if (configured != null && !configured.trim().isEmpty()) {
            return stripTrailingSlash(configured.trim());
        }
        return stripTrailingSlash(baseUrl) + REFUND_PATH;
    }

    /**
     * URLs Finances + Toolkit : variable dédiée prime, sinon base + chemin
     * doc. Même règle que Deposit/Payout/Refund (résolution centralisée,
     * aucune base en dur dans le code métier).
     */
    public static String getWalletBalancesUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_WALLET_BALANCES_URL),
            getApiBaseUrl(), WALLET_BALANCES_PATH);
    }

    public static String getStatementUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_STATEMENT_URL),
            getApiBaseUrl(), STATEMENT_PATH);
    }

    public static String getActiveConfigurationUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_ACTIVE_CONFIGURATION_URL),
            getApiBaseUrl(), ACTIVE_CONFIGURATION_PATH);
    }

    public static String getAvailabilityUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_AVAILABILITY_URL),
            getApiBaseUrl(), AVAILABILITY_PATH);
    }

    public static String getPredictProviderUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_PREDICT_PROVIDER_URL),
            getApiBaseUrl(), PREDICT_PROVIDER_PATH);
    }

    public static String getPublicKeysUrl() {
        return resolveSuffixedUrl(System.getenv(ENV_PUBLIC_KEYS_URL),
            getApiBaseUrl(), PUBLIC_KEYS_PATH);
    }

    static String resolveSuffixedUrl(String configured, String baseUrl, String path) {
        if (configured != null && !configured.trim().isEmpty()) {
            return stripTrailingSlash(configured.trim());
        }
        return stripTrailingSlash(baseUrl) + path;
    }

    private static String stripTrailingSlash(String url) {
        String u = url;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** Token brut (null si absent). Ne jamais logger ni retourner au frontend. */
    public static String getApiToken() {
        String token = System.getenv(ENV_TOKEN);
        if (token == null || token.trim().isEmpty()) return null;
        return token.trim();
    }

    public static boolean isConfigured() {
        return getApiToken() != null;
    }

    /**
     * Garde d'entrée des appels PawaPay : erreur serveur claire si le token
     * manque, SANS jamais révéler sa valeur.
     */
    public static void requireConfigured() throws AppException {
        checkToken(getApiToken());
    }

    static void checkToken(String token) throws AppException {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 503,
                "PawaPay API token is not configured. Set PAWAPAY_API_TOKEN in the environment.");
        }
    }

    /**
     * Vue de statut SANS secret (pour /config-status et les logs de boot) :
     * présence du token uniquement, jamais sa valeur. URLs non secrètes
     * affichées en valeur (présence = non vide).
     */
    public static Map<String, Object> statusSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", isConfigured());
        m.put("token", isConfigured() ? "PRESENT" : "MISSING");
        m.put("environment", getEnvironment().toUpperCase());
        m.put("apiUrl", getApiBaseUrl());
        m.put("depositUrl", getDepositUrl());
        m.put("payoutUrl", getPayoutUrl());
        m.put("refundUrl", getRefundUrl());
        m.put("walletBalancesUrl", getWalletBalancesUrl());
        m.put("statementUrl", getStatementUrl());
        m.put("activeConfigurationUrl", getActiveConfigurationUrl());
        m.put("availabilityUrl", getAvailabilityUrl());
        m.put("predictProviderUrl", getPredictProviderUrl());
        m.put("publicKeysUrl", getPublicKeysUrl());
        m.put("currency", CURRENCY);
        m.put("country", COUNTRY);
        return m;
    }

    /**
     * Méthode checkout MBOA → provider PawaPay (codes vérifiés :
     * https://docs.pawapay.io/v2/docs/providers, pays CMR).
     * Retourne null si la méthode n'est pas éligible à PawaPay.
     */
    public static String toPawaPayProvider(String method) {
        if ("MOBILE_MONEY_MTN".equals(method)) return "MTN_MOMO_CMR";
        if ("MOBILE_MONEY_ORANGE".equals(method)) return "ORANGE_CMR";
        return null;
    }

    /**
     * Opérateurs proposés au checkout : issus de la configuration (jamais
     * inventés). MTN_MOMO_CMR / ORANGE_CMR, devise XAF, pays CMR.
     */
    public static java.util.List<Map<String, Object>> providers() {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        list.add(provider("MOBILE_MONEY_MTN", "MTN_MOMO_CMR", "MTN Mobile Money"));
        list.add(provider("MOBILE_MONEY_ORANGE", "ORANGE_CMR", "Orange Money"));
        return list;
    }

    private static Map<String, Object> provider(String method, String code, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", method);
        m.put("provider", code);
        m.put("label", label);
        m.put("currency", CURRENCY);
        m.put("country", COUNTRY);
        return m;
    }
}
