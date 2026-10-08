package com.ecommerce.pawapay;

import com.ecommerce.dao.PlatformSettingsDAO;
import com.ecommerce.dao.StatementDAO;
import com.ecommerce.model.PawaPayStatement;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Relevés financiers PawaPay (Merchant API v2, doc officielle).
 *
 * Cycle : POST /v2/statements {wallet, callbackUrl(HTTPS), startDate,
 * endDate(≤31j), compressed?} → ACCEPTED + statementId (généré PAR PawaPay,
 * persisté APRÈS l'appel) → final par polling GET /v2/statements/{id} ou
 * callback POST vers la callbackUrl (PROCESSING/COMPLETED/FAILED).
 *
 * Diagnostic/audit UNIQUEMENT : AUCUNE écriture comptable n'en découle
 * (le journal BAZAAR reste la source comptable interne). downloadUrl
 * expire après 1 jour (doc) : toujours revérifier avant usage.
 */
public class PawaPayStatementService {

    private static final Logger LOG = Logger.getLogger(PawaPayStatementService.class.getName());

    /** Clé settings : URL publique HTTPS recevant les callbacks statements. */
    public static final String SETTING_CALLBACK_URL = "pawapay.statement.callback_url";

    /** Durée max d'un relevé : 31 jours (doc). */
    public static final long MAX_RANGE_DAYS = 31;

    private final StatementDAO statementDAO = new StatementDAO();
    private final PlatformSettingsDAO settingsDAO = new PlatformSettingsDAO();

    // ------------------------------------------------------------------
    // Initiation
    // ------------------------------------------------------------------

    /**
     * Demande un relevé (admin MONEY : opération PSP).
     * Valide wallet + plage (≤31j) + callbackUrl HTTPS configurée AVANT
     * l'appel (PawaPay la refuse sinon : INVALID_CALLBACK_URL).
     */
    public PawaPayStatement initiate(String country, String currency, String provider,
            String startDate, String endDate, boolean compressed, Integer adminId)
            throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        String c = requireCountry(country);
        String cur = requireCurrency(currency);
        String[] range = requireRange(startDate, endDate);
        String callbackUrl = statementCallbackUrl();
        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("country", c);
        wallet.put("currency", cur);
        if (provider != null && !provider.trim().isEmpty()) {
            wallet.put("provider", provider.trim());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wallet", wallet);
        body.put("callbackUrl", callbackUrl);
        body.put("startDate", range[0]);
        body.put("endDate", range[1]);
        body.put("compressed", compressed);
        Map<String, Object> resp;
        try {
            resp = new PawaPayClient().initiateStatement(body);
        } catch (AppException e) {
            LOG.warning(() -> "PawaPay Statement request failed: wallet=" + c + "/" + cur);
            throw e;
        }
        String status = str(resp.get("status"));
        String statementId = str(resp.get("statementId"));
        if (!"ACCEPTED".equals(status) || statementId == null || statementId.isEmpty()) {
            String reason = PawaPayPaymentService.failureOf(resp);
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                "Relevé refusé par le prestataire"
                    + (reason == null ? "" : " (" + reason + ")"));
        }
        PawaPayStatement s = new PawaPayStatement();
        s.setStatementId(statementId);
        s.setStatus("ACCEPTED");
        s.setWalletCountry(c);
        s.setWalletCurrency(cur);
        s.setWalletProvider(provider == null || provider.trim().isEmpty() ? null : provider.trim());
        s.setStartDate(range[0]);
        s.setEndDate(range[1]);
        s.setCallbackUrl(callbackUrl);
        s.setRequestedBy(adminId);
        try {
            s.setId(statementDAO.create(s));
        } catch (SQLException e) {
            PawaPayStatement raced = findQuiet(statementId);
            if (raced != null) return raced;
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LOG.info(() -> "PawaPay Statement accepted: statementId=" + statementId);
        return reload(s.getId());
    }

    // ------------------------------------------------------------------
    // Statut / réconciliation
    // ------------------------------------------------------------------

    /** Statut d'un relevé par statementId, réconcilié si non final. */
    public Map<String, Object> getStatementStatus(String statementId)
            throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        if (statementId == null || statementId.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Identifiant de relevé requis");
        }
        PawaPayStatement s = findOrFail(statementId.trim());
        reconcile(s);
        PawaPayStatement fresh = reload(s.getId());
        return maskedView(fresh != null ? fresh : s);
    }

    public List<PawaPayStatement> recent(int limit) throws AppException {
        try {
            return statementDAO.findRecent(limit);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /** Réconciliation : GET distant → transition autorisée uniquement. */
    public void reconcile(PawaPayStatement s) throws AppException, SQLException {
        if (s == null || isFinalStatus(s.getStatus())) return;
        Map<String, Object> remote = new PawaPayClient().getStatement(s.getStatementId());
        if (!"FOUND".equals(str(remote.get("status")))) {
            // ID émis par PawaPay mais introuvable : on ne l'invente pas en
            // FAILED (comportement non documenté) — on touche et on journalise.
            try {
                statementDAO.touchReconciled(s.getId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                    "Impossible de traiter votre demande.", e);
            }
            LOG.warning(() -> "PawaPay Statement not found (issued ID): statementId="
                + s.getStatementId());
            return;
        }
        applyRemote(s, dataOf(remote));
    }

    @SuppressWarnings("unchecked")
    private void applyRemote(PawaPayStatement s, Map<String, Object> data)
            throws AppException, SQLException {
        if (data == null) return;
        String polled = str(data.get("status"));
        String local = localStatusFor(polled);
        String providerTx = null;
        try {
            if (local != null && isTransitionAllowed(s.getStatus(), local)) {
                String reason = "FAILED".equals(local)
                    ? PawaPayPaymentService.failureOf(data) : null;
                boolean moved = statementDAO.transition(s.getId(), local, reason,
                    str(data.get("downloadUrl")), str(data.get("downloadUrlExpiresAt")),
                    str(data.get("completedAt")), str(data.get("failedAt")));
                if (moved) {
                    LOG.info(() -> "PawaPay Statement " + s.getStatus() + " -> " + local
                        + ": statementId=" + s.getStatementId());
                }
            } else if (local != null) {
                try {
                    statementDAO.touchReconciled(s.getId());
                } catch (SQLException e) {
                    throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                        "Impossible de traiter votre demande.", e);
                }
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    // ------------------------------------------------------------------
    // Callback (POST vers la callbackUrl fournie à la création)
    // ------------------------------------------------------------------

    /**
     * Traite un callback statement. Corps doc non détaillé : seul
     * statementId est exigé ; status optionnel (sinon réconciliation GET).
     * Idempotent : transitions conditionnelles uniquement.
     */
    public Map<String, Object> handleCallback(Map<String, Object> payload, String rawJson)
            throws AppException, SQLException {
        String statementId = payload == null ? null : str(payload.get("statementId"));
        if (statementId == null || statementId.isEmpty()) {
            // Certains comptes envoient "id" ? NON — jamais deviné : 400.
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Notification statement inexploitable");
        }
        LOG.info(() -> "PawaPay statement callback received: statementId=" + statementId);
        PawaPayStatement s = findQuiet(statementId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("statementId", statementId);
        if (s == null) {
            out.put("success", true);
            out.put("known", false);
            return out;
        }
        String status = payload == null ? null : str(payload.get("status"));
        boolean transitioned = false;
        if (status != null && !status.isEmpty()) {
            String local = localStatusFor(status);
            if (local != null && isFinalStatus(local)) {
                transitioned = applyFinalFromCallback(s, local, payload);
            } else {
                // Statut non-final ou inconnu : la vérité vient du GET.
                reconcile(s);
                transitioned = false;
            }
        } else {
            reconcile(s);
        }
        PawaPayStatement fresh = reload(s.getId());
        out.put("success", true);
        out.put("known", true);
        out.put("duplicate", !transitioned);
        out.put("transitioned", transitioned);
        if (fresh != null) out.put("localStatus", fresh.getStatus());
        return out;
    }

    private boolean applyFinalFromCallback(PawaPayStatement s, String local,
            Map<String, Object> payload) throws AppException {
        // Réconciliation serveur prime même ici : sans relecture réussie et
        // PawaPay configuré, on diffère (exception → deferred:true côté servlet).
        try {
            reconcile(s);
            PawaPayStatement fresh = reload(s.getId());
            if (fresh != null) s = fresh;
        } catch (Exception e) {
            if (com.ecommerce.pawapay.PawaPayConfig.isConfigured()) {
                throw new AppException(com.ecommerce.util.ErrorCodes.PROVIDER_UNAVAILABLE, 502,
                    "Réconciliation PawaPay indisponible, callback différé");
            }
        }
        if (isFinalStatus(s.getStatus())) return false;
        String reason = "FAILED".equals(local) ? PawaPayPaymentService.failureOf(payload) : null;
        try {
            return statementDAO.transition(s.getId(), local, reason,
                str(payload.get("downloadUrl")), str(payload.get("downloadUrlExpiresAt")),
                str(payload.get("completedAt")), str(payload.get("failedAt")));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    // ------------------------------------------------------------------
    // Validation pure (testée)
    // ------------------------------------------------------------------

    static String requireCountry(String country) throws AppException {
        if (country == null || !country.trim().matches("[A-Z]{3}")) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Pays invalide (ISO 3166-1 alpha-3, ex : CMR)");
        }
        return country.trim();
    }

    static String requireCurrency(String currency) throws AppException {
        if (currency == null || !currency.trim().matches("[A-Z]{3}")) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Devise invalide (ISO 4217, ex : XAF)");
        }
        return currency.trim();
    }

    /**
     * Plage RFC3339 : start < end, durée ≤ 31 jours. Retourne [start, end]
     * normalisés. Accepte 'Z'/offset ou date-heure locale (supposée UTC).
     */
    static String[] requireRange(String start, String end) throws AppException {
        OffsetDateTime s = parseDateTime(start, "début");
        OffsetDateTime e = parseDateTime(end, "fin");
        if (!e.isAfter(s)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Plage invalide : fin après début requise");
        }
        if (Duration.between(s, e).toDays() > MAX_RANGE_DAYS) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Plage invalide : 31 jours maximum (doc PawaPay)");
        }
        return new String[]{s.toString(), e.toString()};
    }

    static OffsetDateTime parseDateTime(String v, String label) throws AppException {
        if (v == null || v.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Date " + label + " requise");
        }
        String t = v.trim();
        try {
            return OffsetDateTime.parse(t);
        } catch (Exception ignored) {}
        try {
            return LocalDateTime.parse(t).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {}
        throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
            "Date " + label + " invalide (RFC3339 attendue)");
    }

    /** callbackUrl HTTPS configurée (settings) — exigée AVANT tout appel. */
    String statementCallbackUrl() throws AppException {
        String url;
        try {
            url = settingsDAO.get(SETTING_CALLBACK_URL, "");
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (url == null || !url.trim().toLowerCase().startsWith("https://")) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Callback statements non configuré (HTTPS requise : platform_settings "
                    + SETTING_CALLBACK_URL + ")");
        }
        return url.trim();
    }

    // ------------------------------------------------------------------
    // Statuts (doc : PROCESSING / COMPLETED / FAILED uniquement)
    // ------------------------------------------------------------------

    static boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    static boolean isOpenStatus(String status) {
        return "ACCEPTED".equals(status) || "PROCESSING".equals(status);
    }

    /** Distant → local (noms doc uniquement). */
    static String localStatusFor(String remoteStatus) {
        if ("PROCESSING".equals(remoteStatus)) return "PROCESSING";
        if ("COMPLETED".equals(remoteStatus)) return "COMPLETED";
        if ("FAILED".equals(remoteStatus)) return "FAILED";
        return null;
    }

    /** Transitions non finales autorisées (pure, testée). */
    static boolean isTransitionAllowed(String fromLocal, String toLocal) {
        if (fromLocal == null || toLocal == null) return false;
        if (!isOpenStatus(fromLocal) || !isOpenStatus(toLocal)) return false;
        if (fromLocal.equals(toLocal)) return true;
        // ACCEPTED → PROCESSING uniquement ; PROCESSING → PROCESSING (touch).
        return "ACCEPTED".equals(fromLocal) && "PROCESSING".equals(toLocal);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PawaPayStatement findOrFail(String statementId) throws AppException, SQLException {
        PawaPayStatement s;
        try {
            s = statementDAO.findByStatementId(statementId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (s == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Relevé introuvable");
        }
        return s;
    }

    private PawaPayStatement findQuiet(String statementId) {
        try {
            return statementDAO.findByStatementId(statementId);
        } catch (SQLException e) {
            return null;
        }
    }

    private PawaPayStatement reload(int id) {
        try {
            return statementDAO.findById(id);
        } catch (SQLException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> remote) {
        if (remote == null) return null;
        Object d = remote.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    /** Vue JSON sans secret (jamais de token ; downloadUrl = admin uniquement). */
    public static Map<String, Object> maskedView(PawaPayStatement s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) return m;
        m.put("id", s.getId());
        m.put("statementId", s.getStatementId());
        m.put("status", s.getStatus());
        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("country", s.getWalletCountry());
        wallet.put("currency", s.getWalletCurrency());
        wallet.put("provider", s.getWalletProvider());
        m.put("wallet", wallet);
        m.put("startDate", s.getStartDate());
        m.put("endDate", s.getEndDate());
        m.put("downloadUrl", s.getDownloadUrl());
        m.put("downloadUrlExpiresAt", s.getDownloadUrlExpiresAt());
        m.put("downloadExpired", isExpired(s.getDownloadUrlExpiresAt()));
        m.put("completedAt", s.getCompletedAt());
        m.put("failedAt", s.getFailedAt());
        m.put("failureReason", s.getFailureReason());
        m.put("requestedBy", s.getRequestedBy());
        m.put("createdAt", s.getCreatedAt());
        m.put("lastReconciledAt", s.getLastReconciledAt());
        return m;
    }

    /** downloadUrl expirée (> 1 jour, doc) ? Inconnue = true (revérifier). */
    static boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isEmpty()) return true;
        try {
            return OffsetDateTime.now(ZoneOffset.UTC).isAfter(parseDateTime(expiresAt, "x"));
        } catch (AppException e) {
            return true;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
