package com.ecommerce.pawapay;

import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Client HTTP PawaPay (Merchant API v2, doc officielle
 * https://docs.pawapay.io/). Java 17 natif (java.net.http), sans
 * dépendance supplémentaire.
 *
 * Authentification : {@code Authorization: Bearer <PAWAPAY_API_TOKEN>}
 * envoyé uniquement depuis ce backend. Le token n'est JAMAIS loggé :
 * les logs ne contiennent que depositId, statuts et motifs PawaPay.
 *
 * Endpoints utilisés (tous vérifiés dans la doc v2) :
 * - POST /v2/deposits                      : initier un dépôt (collection)
 * - GET  /v2/deposits/{depositId}           : statut d'un dépôt
 * - POST /v2/deposits/resend-callback/{id}  : renvoyer le callback final
 * - POST /v2/payouts                       : initier un versement fournisseur
 * - POST /v2/payouts/bulk                  : versements groupés (max 20)
 * - GET  /v2/payouts/{payoutId}             : statut d'un versement
 * - POST /v2/payouts/resend-callback/{id}   : renvoyer le callback final
 * - POST /v2/payouts/fail-enqueued/{id}     : annuler un versement ENQUEUED
 * - POST /v2/refunds                       : rembourser un dépôt
 * - GET  /v2/refunds/{refundId}             : statut d'un remboursement
 * - POST /v2/refunds/resend-callback/{id}   : renvoyer le callback final
 * - POST /v2/refunds/fail-enqueued/{id}     : annuler un remboursement ENQUEUED
 * - GET  /v2/wallet-balances[?country]      : soldes wallets (lecture seule)
 * - POST /v2/statements                     : demander un relevé (async)
 * - GET  /v2/statements/{id}                : statut d'un relevé
 * - POST /v2/predict-provider               : prédire le provider d'un MSISDN
 * - GET  /v2/public-key/http                : clés publiques callbacks (tableau)
 * - GET  /v2/active-conf?country=CMR        : providers configurés
 * - GET  /v2/availability                   : disponibilité des providers
 */
public class PawaPayClient {

    private static final Logger LOG = Logger.getLogger(PawaPayClient.class.getName());

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final String depositUrl;
    private final String payoutUrl;
    private final String refundUrl;
    private final String walletBalancesUrl;
    private final String statementUrl;
    private final String activeConfigurationUrl;
    private final String availabilityUrl;
    private final String predictProviderUrl;
    private final String publicKeysUrl;
    private final String apiToken;
    private final HttpClient http;

    /** Client de production : lit la config (exige le token). */
    public PawaPayClient() throws AppException {
        PawaPayConfig.requireConfigured();
        this.baseUrl = PawaPayConfig.getApiBaseUrl();
        this.depositUrl = PawaPayConfig.getDepositUrl();
        this.payoutUrl = PawaPayConfig.getPayoutUrl();
        this.refundUrl = PawaPayConfig.getRefundUrl();
        this.walletBalancesUrl = PawaPayConfig.getWalletBalancesUrl();
        this.statementUrl = PawaPayConfig.getStatementUrl();
        this.activeConfigurationUrl = PawaPayConfig.getActiveConfigurationUrl();
        this.availabilityUrl = PawaPayConfig.getAvailabilityUrl();
        this.predictProviderUrl = PawaPayConfig.getPredictProviderUrl();
        this.publicKeysUrl = PawaPayConfig.getPublicKeysUrl();
        this.apiToken = PawaPayConfig.getApiToken();
        this.http = defaultHttpClient();
    }

    /** Client injectable (tests contre mock local, sans token réel). */
    PawaPayClient(String baseUrl, String depositUrl, String apiToken, HttpClient http) {
        this.baseUrl = baseUrl;
        this.depositUrl = depositUrl;
        this.payoutUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.PAYOUT_PATH;
        this.refundUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.REFUND_PATH;
        this.walletBalancesUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.WALLET_BALANCES_PATH;
        this.statementUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.STATEMENT_PATH;
        this.activeConfigurationUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.ACTIVE_CONFIGURATION_PATH;
        this.availabilityUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.AVAILABILITY_PATH;
        this.predictProviderUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.PREDICT_PROVIDER_PATH;
        this.publicKeysUrl = stripTrailingSlash(baseUrl) + PawaPayConfig.PUBLIC_KEYS_PATH;
        this.apiToken = apiToken;
        this.http = http != null ? http : defaultHttpClient();
    }

    private static String stripTrailingSlash(String url) {
        String u = url == null ? "" : url;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    // ------------------------------------------------------------------
    // Dépôts (collections MTN / Orange)
    // ------------------------------------------------------------------

    /**
     * Initie un dépôt. Réponse d'initiation : ACCEPTED (pris en charge),
     * REJECTED (+ failureReason) ou DUPLICATE_IGNORED (même depositId déjà
     * accepté — idempotent côté PawaPay). ACCEPTED ne signifie PAS payé :
     * le statut final arrive par callback ou par {@link #getDeposit}.
     */
    /**
     * Corps JSON du Deposit, strictement conforme à la doc officielle :
     * depositId (UUIDv4), amount (string), currency, payer {type MMO,
     * accountDetails {phoneNumber, provider}}, clientReferenceId,
     * customerMessage, metadata (LISTE d'objets, pas un objet).
     */
    static Map<String, Object> buildDepositBody(String depositId, String amount,
            String currency, String phoneNumber, String provider,
            String clientReferenceId, String customerMessage,
            java.util.List<Map<String, String>> metadata) {
        Map<String, Object> accountDetails = new LinkedHashMap<>();
        accountDetails.put("phoneNumber", phoneNumber);
        accountDetails.put("provider", provider);
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("type", "MMO");
        payer.put("accountDetails", accountDetails);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("depositId", depositId);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("payer", payer);
        if (clientReferenceId != null && !clientReferenceId.isEmpty()) {
            body.put("clientReferenceId", clientReferenceId);
        }
        if (customerMessage != null && !customerMessage.isEmpty()) {
            body.put("customerMessage", customerMessage);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> initiateDeposit(String depositId, String amount,
            String currency, String phoneNumber, String provider,
            String clientReferenceId, String customerMessage,
            java.util.List<Map<String, String>> metadata) throws AppException {
        Map<String, Object> body = buildDepositBody(depositId, amount, currency,
            phoneNumber, provider, clientReferenceId, customerMessage, metadata);
        Map<String, Object> resp = postUrl(depositUrl, body);
        LOG.info(() -> "PawaPay Deposit request created: depositId=" + depositId
            + " status=" + resp.get("status") + " provider=" + provider);
        return resp;
    }

    /**
     * Statut courant d'un dépôt : {"status":"FOUND"/"NOT_FOUND",
     * "data":{depositId,status,...}}. Statuts data.status : ACCEPTED,
     * PROCESSING, IN_RECONCILIATION, COMPLETED, FAILED.
     */
    public Map<String, Object> getDeposit(String depositId) throws AppException {
        return get("/v2/deposits/" + depositId);
    }

    /** Redemande le callback final d'un dépôt terminé (reconciliation). */
    public Map<String, Object> resendCallback(String depositId) throws AppException {
        return post("/v2/deposits/resend-callback/" + depositId, new LinkedHashMap<>());
    }

    // ------------------------------------------------------------------
    // Versements fournisseurs (payouts) et remboursements (refunds)
    // ------------------------------------------------------------------

    /**
     * Corps JSON du Payout, strictement conforme à la doc officielle :
     * payoutId (UUIDv4), amount (string), currency,
     * recipient {type MMO, accountDetails {phoneNumber, provider}},
     * clientReferenceId, customerMessage, metadata (LISTE d'objets).
     */
    static Map<String, Object> buildPayoutBody(String payoutId, String amount,
            String currency, String phoneNumber, String provider,
            String clientReferenceId, String customerMessage,
            java.util.List<Map<String, String>> metadata) {
        Map<String, Object> accountDetails = new LinkedHashMap<>();
        accountDetails.put("phoneNumber", phoneNumber);
        accountDetails.put("provider", provider);
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("type", "MMO");
        recipient.put("accountDetails", accountDetails);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payoutId", payoutId);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("recipient", recipient);
        if (clientReferenceId != null && !clientReferenceId.isEmpty()) {
            body.put("clientReferenceId", clientReferenceId);
        }
        if (customerMessage != null && !customerMessage.isEmpty()) {
            body.put("customerMessage", customerMessage);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        return body;
    }

    /**
     * Initie un versement. Réponse : ACCEPTED, REJECTED (+ failureReason)
     * ou DUPLICATE_IGNORED (même payoutId — idempotent côté PawaPay).
     * ACCEPTED ne signifie PAS versé : seul COMPLETED (callback ou
     * polling serveur) vaut versement confirmé.
     */
    public Map<String, Object> initiatePayout(String payoutId, String amount,
            String currency, String phoneNumber, String provider,
            String clientReferenceId, String customerMessage,
            java.util.List<Map<String, String>> metadata) throws AppException {
        Map<String, Object> body = buildPayoutBody(payoutId, amount, currency,
            phoneNumber, provider, clientReferenceId, customerMessage, metadata);
        Map<String, Object> resp = postUrl(payoutUrl, body);
        LOG.info(() -> "PawaPay Payout request created: payoutId=" + payoutId
            + " status=" + resp.get("status") + " provider=" + provider);
        return resp;
    }

    /**
     * Versements groupés (doc officielle POST /v2/payouts/bulk).
     * Corps = TABLEAU de PayoutInitiationRequest (max 20, vérifié appelant).
     * Réponse = TABLEAU de {payoutId, status, created?, failureReason?}
     * (un résultat PAR payout, dans un ordre quelconque : apparier par
     * payoutId, jamais par index).
     */
    public static final int BULK_MAX_ITEMS = 20;

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> initiateBulkPayouts(
            java.util.List<Map<String, Object>> items) throws AppException {
        if (items == null || items.isEmpty()) {
            throw new AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400,
                "Bulk vide : au moins 1 versement requis");
        }
        if (items.size() > BULK_MAX_ITEMS) {
            throw new AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400,
                "Bulk limité à " + BULK_MAX_ITEMS + " versements par appel PawaPay");
        }
        String json = JsonUtil.toJson(items);
        java.net.http.HttpRequest req = baseRequest(payoutUrl + "/bulk", payoutUrl + "/bulk")
            .header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
            .build();
        Map<String, Object> wrapped = sendForBulk(req, payoutUrl + "/bulk");
        Object data = wrapped.get("data");
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (data instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) data) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        LOG.info(() -> "PawaPay Bulk payout submitted: items=" + items.size()
            + " results=" + out.size());
        return out;
    }

    /**
     * Annule un versement ENQUEUED (doc officielle
     * POST /v2/payouts/fail-enqueued/{payoutId}).
     * Réponse : ACCEPTED (→ FAILED / MANUALLY_CANCELLED + callback) ou
     * REJECTED (NOT_FOUND / INVALID_STATE : pas en file d'attente).
     * À n'appeler qu'après vérification d'état (GET), jamais sur un
     * payout COMPLETED/FAILED.
     */
    public Map<String, Object> cancelEnqueuedPayout(String payoutId) throws AppException {
        Map<String, Object> resp = post("/v2/payouts/fail-enqueued/" + payoutId,
            new LinkedHashMap<>());
        LOG.info(() -> "PawaPay fail-enqueued requested: payoutId=" + payoutId
            + " status=" + resp.get("status"));
        return resp;
    }

    /**
     * Statut courant d'un versement : {"status":"FOUND"/"NOT_FOUND",
     * "data":{payoutId,status,...}}. Statuts data.status : ACCEPTED,
     * ENQUEUED, PROCESSING, IN_RECONCILIATION, COMPLETED, FAILED.
     */
    public Map<String, Object> getPayout(String payoutId) throws AppException {
        return get("/v2/payouts/" + payoutId);
    }

    /** Redemande le callback final d'un versement terminé. */
    public Map<String, Object> resendPayoutCallback(String payoutId) throws AppException {
        return post("/v2/payouts/resend-callback/" + payoutId, new LinkedHashMap<>());
    }

    /**
     * Corps JSON du Refund, strictement conforme à la doc officielle :
     * refundId (UUIDv4), depositId, amount (string), currency,
     * clientReferenceId, metadata (LISTE d'objets, ≤ 10 — comme
     * deposit/payout ; jamais de secret, jamais de PII).
     */
    static Map<String, Object> buildRefundBody(String refundId, String depositId,
            String amount, String currency, String clientReferenceId,
            java.util.List<Map<String, String>> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refundId", refundId);
        body.put("depositId", depositId);
        if (amount != null && !amount.isEmpty()) body.put("amount", amount);
        if (currency != null && !currency.isEmpty()) body.put("currency", currency);
        if (clientReferenceId != null && !clientReferenceId.isEmpty()) {
            body.put("clientReferenceId", clientReferenceId);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata.subList(0, Math.min(10, metadata.size())));
        }
        return body;
    }

    /**
     * Initie un remboursement (total : amount = montant du dépôt).
     * Corps officiel : refundId (UUIDv4) + depositId + amount + currency
     * (PaymentInitiationRequest les exige) + clientReferenceId optionnel
     * + metadata optionnelle. Réponse : ACCEPTED / REJECTED /
     * DUPLICATE_IGNORED.
     */
    public Map<String, Object> initiateRefund(String refundId, String depositId,
            String amount, String currency, String clientReferenceId,
            java.util.List<Map<String, String>> metadata) throws AppException {
        Map<String, Object> body = buildRefundBody(refundId, depositId, amount,
            currency, clientReferenceId, metadata);
        Map<String, Object> resp = postUrl(refundUrl, body);
        LOG.info(() -> "PawaPay Refund request created: refundId=" + refundId
            + " status=" + resp.get("status"));
        return resp;
    }

    /** Compatibilité : sans metadata (ne pas utiliser pour les nouveaux flux). */
    public Map<String, Object> initiateRefund(String refundId, String depositId,
            String amount, String currency, String clientReferenceId) throws AppException {
        return initiateRefund(refundId, depositId, amount, currency, clientReferenceId, null);
    }

    /**
     * Statut courant d'un remboursement : {"status":"FOUND"/"NOT_FOUND",
     * "data":{refundId,status,...}}. Statuts data.status : ACCEPTED,
     * ENQUEUED, PROCESSING, IN_RECONCILIATION, COMPLETED, FAILED.
     */
    public Map<String, Object> getRefund(String refundId) throws AppException {
        return get("/v2/refunds/" + refundId);
    }

    /** Redemande le callback final d'un remboursement terminé. */
    public Map<String, Object> resendRefundCallback(String refundId) throws AppException {
        return post("/v2/refunds/resend-callback/" + refundId, new LinkedHashMap<>());
    }

    /**
     * Annule un remboursement ENQUEUED (doc officielle
     * POST /v2/refunds/fail-enqueued/{refundId}).
     * Réponse : ACCEPTED (→ FAILED / MANUALLY_CANCELLED + callback) ou
     * REJECTED (NOT_FOUND / INVALID_STATE : pas en file d'attente).
     * À n'appeler qu'après vérification d'état (GET), jamais sur un
     * refund COMPLETED/FAILED.
     */
    public Map<String, Object> cancelEnqueuedRefund(String refundId) throws AppException {
        Map<String, Object> resp = post("/v2/refunds/fail-enqueued/" + refundId,
            new LinkedHashMap<>());
        LOG.info(() -> "PawaPay fail-enqueued refund requested: refundId=" + refundId
            + " status=" + resp.get("status"));
        return resp;
    }

    // ------------------------------------------------------------------
    // Outillage (providers / disponibilité)
    // ------------------------------------------------------------------

    public Map<String, Object> getActiveConfiguration(String country) throws AppException {
        return getActiveConfiguration(country, null);
    }

    /**
     * Configuration active (doc : GET /v2/active-conf[?country&operationType]).
     * Réponse : {companyName, signatureConfiguration{signedRequestsOnly,
     * signedCallbacks}, countries:[...]}. Surcharge conservée (compat).
     */
    public Map<String, Object> getActiveConfiguration(String country, String operationType)
            throws AppException {
        String url = activeConfigurationUrl;
        boolean first = true;
        if (country != null && !country.isEmpty()) {
            url += (first ? "?" : "&") + "country=" + country;
            first = false;
        }
        if (operationType != null && !operationType.isEmpty()) {
            url += (first ? "?" : "&") + "operationType=" + operationType;
        }
        HttpRequest req = baseRequest(url, url).GET().build();
        return send(req, url);
    }

    public Map<String, Object> getAvailability(String country, String operationType) throws AppException {
        String url = availabilityUrl;
        boolean first = true;
        if (country != null && !country.isEmpty()) {
            url += (first ? "?" : "&") + "country=" + country;
            first = false;
        }
        if (operationType != null && !operationType.isEmpty()) {
            url += (first ? "?" : "&") + "operationType=" + operationType;
        }
        return getListUrl(url);
    }

    // ------------------------------------------------------------------
    // Finances (doc : soldes = information distante, jamais comptable)
    // ------------------------------------------------------------------

    /**
     * Soldes des wallets (doc : GET /v2/wallet-balances[?country]).
     * Réponse : {balances:[{country, balance(string), currency, provider}]}.
     * Lecture seule : AUCUNE écriture comptable n'en découle (§38).
     */
    public Map<String, Object> getWalletBalances(String country) throws AppException {
        String url = walletBalancesUrl;
        if (country != null && !country.isEmpty()) url += "?country=" + country;
        HttpRequest req = baseRequest(url, url).GET().build();
        return send(req, url);
    }

    /**
     * Demande de relevé (doc : POST /v2/statements).
     * Corps : {wallet{country, currency, provider?}, callbackUrl (HTTPS
     * requise), startDate, endDate (RFC3339, ≤ 31 jours), compressed?}.
     * Réponse : {statementId (généré PAR PawaPay), status ACCEPTED/REJECTED,
     * created?, failureReason?}. ACCEPTED ≠ généré (async).
     */
    public Map<String, Object> initiateStatement(Map<String, Object> body) throws AppException {
        Map<String, Object> resp = postUrl(statementUrl, body);
        LOG.info(() -> "PawaPay Statement requested: statementId=" + resp.get("statementId")
            + " status=" + resp.get("status"));
        return resp;
    }

    /**
     * Statut d'un relevé (doc : GET /v2/statements/{id}).
     * Réponse : {status: FOUND/NOT_FOUND, data?: {statementId,
     * status: PROCESSING/COMPLETED/FAILED, wallet, created, startDate,
     * endDate, fileSize?, downloadUrl? (expire 1 jour), downloadUrlExpiresAt?,
     * completedAt?, failedAt?, failureReason?}}.
     */
    public Map<String, Object> getStatement(String statementId) throws AppException {
        String url = statementUrl + "/" + statementId;
        HttpRequest req = baseRequest(url, url).GET().build();
        return send(req, url);
    }

    // ------------------------------------------------------------------
    // Toolkit : prédiction + clés publiques
    // ------------------------------------------------------------------

    /**
     * Prédiction du provider (doc : POST /v2/predict-provider {phoneNumber}).
     * Réponse : {country, provider, phoneNumber} — indication (taux d'erreur
     * 0.12 % doc), JAMAIS une garantie de disponibilité ni le provider
     * imposé d'un Deposit/Payout.
     */
    public Map<String, Object> predictProvider(String phoneNumber) throws AppException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", phoneNumber);
        Map<String, Object> resp = postUrl(predictProviderUrl, body);
        LOG.info(() -> "PawaPay predict provider: provider=" + resp.get("provider")
            + " country=" + resp.get("country"));
        return resp;
    }

    /**
     * Clés publiques de signature des callbacks (doc : GET /v2/public-key/http).
     * Réponse : TABLEAU [{id, key}]. Retournée telle quelle sous "data"
     * (jamais de token ici — ce ne sont PAS des secrets d'API).
     */
    public java.util.List<Map<String, Object>> getPublicKeys() throws AppException {
        HttpRequest req = baseRequest(publicKeysUrl, publicKeysUrl)
            .GET().build();
        Map<String, Object> wrapped = sendForBulk(req, publicKeysUrl);
        Object data = wrapped.get("data");
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (data instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) data) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        LOG.info(() -> "PawaPay public keys fetched: count=" + out.size());
        return out;
    }

    // ------------------------------------------------------------------
    // HTTP interne
    // ------------------------------------------------------------------

    private Map<String, Object> get(String path) throws AppException {
        HttpRequest req = baseRequest(baseUrl + path, path).GET().build();
        return send(req, path);
    }

    /**
     * GET dont la réponse est un TABLEAU JSON (doc : /v2/availability,
     * /v2/public-key/http). Retourne {"data": [...]} (jamais null).
     */
    private Map<String, Object> getList(String path) throws AppException {
        HttpRequest req = baseRequest(baseUrl + path, path).GET().build();
        return sendForBulk(req, path);
    }

    /** Variante URL complète (endpoints configurables). */
    private Map<String, Object> getListUrl(String url) throws AppException {
        HttpRequest req = baseRequest(url, url).GET().build();
        return sendForBulk(req, url);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) throws AppException {
        return postUrl(baseUrl + path, body);
    }

    /** POST vers une URL complète (le Deposit utilise PAWAPAY_DEPOSIT_URL). */
    private Map<String, Object> postUrl(String url, Map<String, Object> body) throws AppException {
        String json = JsonUtil.toJson(body == null ? new LinkedHashMap<>() : body);
        HttpRequest req = baseRequest(url, url)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return send(req, url);
    }

    private HttpRequest.Builder baseRequest(String url, String logLabel) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiToken)
            .header("Accept", "application/json");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(HttpRequest req, String path) throws AppException {
        String payload = sendRaw(req, path);
        if (payload.isEmpty()) return new LinkedHashMap<>();
        try {
            Object parsed = JsonUtil.fromJson(payload, Map.class);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
        } catch (Exception e) {
            LOG.warning(() -> "PawaPay invalid JSON response: path=" + path);
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Réponse du prestataire inexploitable, réessayez.", e);
        }
        return new LinkedHashMap<>();
    }

    /**
     * Envoi bulk : même gestion HTTPerreurs que {@link #send}, mais réponse
     * = TABLEAU JSON. Retourne {"data": [...]} (liste vide si corps vide).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendForBulk(HttpRequest req, String path) throws AppException {
        String payload = sendRaw(req, path);
        Map<String, Object> out = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (!payload.isEmpty()) {
            try {
                Object parsed = JsonUtil.fromJson(payload, java.util.List.class);
                if (parsed instanceof java.util.List) {
                    for (Object o : (java.util.List<Object>) parsed) {
                        if (o instanceof Map) items.add((Map<String, Object>) o);
                    }
                }
            } catch (Exception e) {
                LOG.warning(() -> "PawaPay invalid bulk JSON response: path=" + path);
                throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                    "Réponse du prestataire inexploitable, réessayez.", e);
            }
        }
        out.put("data", items);
        return out;
    }

    /** Envoi HTTP + gestion des codes (jamais de secret en log/erreur). */
    private String sendRaw(HttpRequest req, String path) throws AppException {
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Timeout : NE JAMAIS recréer un paiement aussitôt — l'id existe
            // peut-être déjà côté PawaPay (vérifier par polling).
            if (e instanceof java.net.http.HttpTimeoutException) {
                LOG.warning(() -> "PawaPay request timed out: path=" + path);
                throw new AppException(ErrorCodes.PAWAPAY_TIMEOUT, 503,
                    "Délai dépassé : vérifiez le statut avant de réessayer (aucun nouveau paiement créé).", e);
            }
            LOG.warning(() -> "PawaPay request failed (network): path=" + path);
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Prestataire de paiement injoignable, réessayez.", e);
        }
        int code = resp.statusCode();
        String payload = resp.body() == null ? "" : resp.body();
        if (code == 401 || code == 403) {
            // Jamais de détail d'auth au frontend, jamais de token en log.
            LOG.warning(() -> "PawaPay request rejected (auth " + code + "): path=" + path);
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Prestataire de paiement indisponible, réessayez.");
        }
        if (code == 404) {
            LOG.warning(() -> "PawaPay resource not found: path=" + path);
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404,
                "Paiement introuvable auprès du prestataire.");
        }
        if (code == 429) {
            LOG.warning(() -> "PawaPay rate limited: path=" + path);
            throw new AppException(ErrorCodes.RATE_LIMITED, 503,
                "Trop de requêtes vers le prestataire, réessayez dans un instant.");
        }
        if (code == 400) {
            // Incompatibilité de payload : motif PawaPay en log serveur
            // uniquement (jamais de secret), message générique au client.
            LOG.warning(() -> "PawaPay rejected payload: path=" + path
                + " reason=" + failureCodeOf(payload));
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Demande refusée par le prestataire, vérifiez le numéro et réessayez.");
        }
        if (code < 200 || code >= 300) {
            LOG.warning(() -> "PawaPay request failed (HTTP " + code + "): path=" + path);
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Prestataire de paiement indisponible, réessayez.");
        }
        return payload;
    }

    /**
     * Providers avec dépôts supportés, extraits de GET /v2/active-conf
     * (structure : countries[].providers[].currencies[].operationTypes).
     * Parsing défensif : toute forme inattendue = ensemble vide (le backend
     * retombe alors sur la configuration statique vérifiée).
     */
    @SuppressWarnings("unchecked")
    public static java.util.Set<String> parseDepositProviders(Map<String, Object> activeConf) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (activeConf == null) return out;
        try {
            Object countries = activeConf.get("countries");
            if (!(countries instanceof java.util.List)) return out;
            for (Object c : (java.util.List<Object>) countries) {
                if (!(c instanceof Map)) continue;
                Object providers = ((Map<String, Object>) c).get("providers");
                if (!(providers instanceof java.util.List)) continue;
                for (Object p : (java.util.List<Object>) providers) {
                    if (!(p instanceof Map)) continue;
                    Map<String, Object> pm = (Map<String, Object>) p;
                    Object code = pm.get("provider");
                    Object currencies = pm.get("currencies");
                    if (!(code instanceof String) || !(currencies instanceof java.util.List)) continue;
                    for (Object cur : (java.util.List<Object>) currencies) {
                        if (!(cur instanceof Map)) continue;
                        if (hasDepositOp(((Map<String, Object>) cur).get("operationTypes"))) {
                            out.add((String) code);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasDepositOp(Object operationTypes) {
        if (operationTypeIsDeposit(operationTypes)) return true;
        if (operationTypes instanceof Map) {
            return ((Map<String, Object>) operationTypes).containsKey("DEPOSIT");
        }
        if (operationTypes instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) operationTypes) {
                if (operationTypeIsDeposit(o)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean operationTypeIsDeposit(Object o) {
        if ("DEPOSIT".equals(o)) return true;
        if (o instanceof Map) {
            Object t = ((Map<String, Object>) o).get("operationType");
            if ("DEPOSIT".equals(t)) return true;
            Object op = ((Map<String, Object>) o).get("operationTypes");
            if (op != null) return hasDepositOp(op);
        }
        return false;
    }

    /** Code motif PawaPay pour le log serveur (jamais exposé tel quel). */
    @SuppressWarnings("unchecked")
    private static String failureCodeOf(String payload) {
        try {
            Object parsed = JsonUtil.fromJson(payload, Map.class);
            if (parsed instanceof Map) {
                Object fr = ((Map<String, Object>) parsed).get("failureReason");
                if (fr instanceof Map) {
                    Object code = ((Map<String, Object>) fr).get("failureCode");
                    return code == null ? "UNKNOWN" : String.valueOf(code);
                }
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }
}
