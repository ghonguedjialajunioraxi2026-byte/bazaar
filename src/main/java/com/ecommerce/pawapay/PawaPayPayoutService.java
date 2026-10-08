package com.ecommerce.pawapay;

import com.ecommerce.dao.DisputeDAO;
import com.ecommerce.dao.MonetizationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.PayoutDAO;
import com.ecommerce.dao.PlatformSettingsDAO;
import com.ecommerce.dao.RefundDAO;
import com.ecommerce.dao.ShipmentDAO;
import com.ecommerce.dao.SupplierDAO;
import com.ecommerce.model.LedgerEntry;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Refund;
import com.ecommerce.model.Shipment;
import com.ecommerce.model.Supplier;
import com.ecommerce.model.SupplierPayment;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Versements fournisseurs via PawaPay (Merchant API v2, doc officielle).
 *
 * 6 capacités couvertes :
 * 1. Initiate payout (POST /v2/payouts, idempotent par payoutId).
 * 2. Check payout status (GET /v2/payouts/{payoutId}, réconciliation).
 * 3. Resend payout callback (POST /v2/payouts/resend-callback/{id}).
 * 4. Cancel enqueued payout (POST /v2/payouts/fail-enqueued/{id}).
 * 5. Bulk payouts (POST /v2/payouts/bulk, max 20, résultats par payout).
 * 6. Payout callback (réconciliation serveur + journal idempotent).
 *
 * Règle métier — un Payout n'est possible QUE si :
 * commande LIVREE + réception confirmée par le client + dépôt PawaPay
 * COMPLETED + écriture de vente comptabilisée + aucun litige OPEN +
 * aucun refund en cours/terminé + aucun payout déjà terminé.
 *
 * Montant : vente − commission BAZAAR (taux figé du ledger, 1 %) −
 * frais PawaPay configurables (platform_settings) = net fournisseur.
 *
 * Discipline identique au Deposit : payoutId (UUIDv4) persisté AVANT
 * l'appel réseau, ACCEPTED ≠ versé (seul COMPLETED confirmé par
 * callback ou polling serveur vaut versement), idempotence stricte
 * (1 commande = 1 payout, retry = même payoutId), aucun secret en
 * logs/base/réponses.
 */
public class PawaPayPayoutService {

    private static final Logger LOG = Logger.getLogger(PawaPayPayoutService.class.getName());

    /** Raisons de blocage (codes stables, exposés à l'admin/fournisseur). */
    public static final String BLOCK_ORDER_NOT_DELIVERED = "ORDER_NOT_DELIVERED";
    public static final String BLOCK_RECEIPT_NOT_CONFIRMED = "RECEIPT_NOT_CONFIRMED";
    public static final String BLOCK_DISPUTE_OPEN = "DISPUTE_OPEN";
    public static final String BLOCK_REFUND_IN_PROGRESS = "REFUND_IN_PROGRESS";
    public static final String BLOCK_NO_SALE_ENTRY = "NO_SALE_ENTRY";
    public static final String BLOCK_NO_SUPPLIER = "NO_SUPPLIER";
    public static final String BLOCK_SUPPLIER_INACTIVE = "SUPPLIER_INACTIVE";
    public static final String BLOCK_PAYOUT_DETAILS_MISSING = "PAYOUT_DETAILS_MISSING";
    public static final String BLOCK_DEPOSIT_NOT_COMPLETED = "DEPOSIT_NOT_COMPLETED";
    public static final String BLOCK_AMOUNT_INVALID = "AMOUNT_INVALID";

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final DisputeDAO disputeDAO = new DisputeDAO();
    private final RefundDAO refundDAO = new RefundDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final MonetizationDAO monetizationDAO = new MonetizationDAO();
    private final PlatformSettingsDAO settingsDAO = new PlatformSettingsDAO();

    /** Résultat : ligne de payout + flag "existait déjà" (retry sûr). */
    public static class PayoutOutcome {
        public final SupplierPayment payout;
        public final boolean duplicate;

        PayoutOutcome(SupplierPayment payout, boolean duplicate) {
            this.payout = payout;
            this.duplicate = duplicate;
        }
    }

    // ------------------------------------------------------------------
    // Éligibilité (PAYOUT BLOCKED sinon)
    // ------------------------------------------------------------------

    /**
     * Contrôle complet. Retourne {"eligible":true, breakdown...} ou
     * {"eligible":false, "reason":CODE, ...}. Ne crée rien.
     */
    public Map<String, Object> checkEligibility(int orderId) throws AppException, SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        Order o = loadOrder(orderId);
        out.put("orderId", o.getId());
        out.put("orderNumber", o.getOrderNumber());

        if (!"LIVREE".equals(o.getStatus())) {
            return blocked(out, BLOCK_ORDER_NOT_DELIVERED, "Commande non livrée");
        }
        Shipment s;
        try {
            s = shipmentDAO.findByOrder(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (s == null || !s.isReceiptConfirmed()) {
            return blocked(out, BLOCK_RECEIPT_NOT_CONFIRMED, "Réception non confirmée par le client");
        }
        try {
            if (disputeDAO.hasOpenDispute(o.getId())) {
                return blocked(out, BLOCK_DISPUTE_OPEN, "Litige ouvert : versement bloqué");
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        try {
            Refund r = refundDAO.findLatestByOrderId(o.getId());
            if (r != null && !"FAILED".equals(r.getStatus())) {
                return blocked(out, BLOCK_REFUND_IN_PROGRESS, "Remboursement en cours ou effectué");
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LedgerEntry sale = saleEntry(o.getId());
        if (sale == null) {
            return blocked(out, BLOCK_NO_SALE_ENTRY, "Vente non comptabilisée");
        }
        if (o.getSupplierId() <= 0) {
            return blocked(out, BLOCK_NO_SUPPLIER, "Aucun fournisseur affecté");
        }
        Supplier sup = loadSupplier(o.getSupplierId());
        if (sup == null) {
            return blocked(out, BLOCK_NO_SUPPLIER, "Fournisseur introuvable");
        }
        if (!sup.isActive()) {
            return blocked(out, BLOCK_SUPPLIER_INACTIVE, "Compte fournisseur désactivé");
        }
        // Règle de routage : le versement part vers le numéro du MÊME
        // opérateur que le dépôt client (MTN payé → numéro MTN, etc.).
        // Dépôt d'abord (sa devise/provider font foi), coordonnées ensuite.
        Payment deposit = completedDeposit(o.getId());
        if (deposit == null) {
            return blocked(out, BLOCK_DEPOSIT_NOT_COMPLETED,
                "Aucun dépôt PawaPay confirmé (retrait manuel requis)");
        }
        String provider = deposit.getOperator();
        if (!isPayoutProvider(provider)) {
            return blocked(out, BLOCK_DEPOSIT_NOT_COMPLETED,
                "Dépôt non versable via PawaPay");
        }
        String phone = resolveSupplierPhone(sup, provider);
        if (phone == null) {
            out.put("providerConfigured", provider);
            out.put("provider", provider);
            return blocked(out, BLOCK_PAYOUT_DETAILS_MISSING,
                "Numéro " + ("MTN_MOMO_CMR".equals(provider) ? "MTN" : "Orange")
                    + " du fournisseur manquant (espace profil)");
        }
        Map<String, Object> breakdown = breakdown(sale);
        out.putAll(breakdown);
        int net = (int) breakdown.get("net");
        if (net <= 0) {
            return blocked(out, BLOCK_AMOUNT_INVALID, "Montant net invalide");
        }
        out.put("eligible", true);
        out.put("supplierId", sup.getId());
        out.put("provider", provider);
        out.put("phone", phone);
        out.put("depositId", deposit.getProviderTransactionId());
        return out;
    }

    private static Map<String, Object> blocked(Map<String, Object> out, String reason, String message) {
        out.put("eligible", false);
        out.put("reason", reason);
        out.put("message", message);
        return out;
    }

    // ------------------------------------------------------------------
    // Calcul du versement (vente − commission 1 % − frais PawaPay)
    // ------------------------------------------------------------------

    /** Décomposition à partir de l'écriture de vente (taux figé). */
    Map<String, Object> breakdown(LedgerEntry sale) throws AppException {
        int fee;
        try {
            fee = Math.max(0, settingsDAO.getInt(PlatformSettingsDAO.PAWAPAY_PAYOUT_FEE, 0));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return buildBreakdown(sale.getNet(), sale.getCommissionBps(), sale.getCommission(),
            sale.getFixedFee(), sale.getSupplierEarnings(), fee);
    }

    /**
     * Calcul pur du versement (testable sans base) :
     * net = gainsFournisseur − fraisPawaPay (jamais négatif).
     */
    static Map<String, Object> buildBreakdown(int gross, int commissionBps, int commission,
            int fixedFee, int supplierEarnings, int fee) {
        int net = Math.max(0, supplierEarnings - Math.max(0, fee));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gross", gross);
        m.put("commissionBps", commissionBps);
        m.put("commission", commission);
        m.put("fixedFee", fixedFee);
        m.put("supplierEarnings", supplierEarnings);
        m.put("pawapayFee", Math.max(0, fee));
        m.put("net", net);
        return m;
    }

    // ------------------------------------------------------------------
    // Initiation / retry (idempotents)
    // ------------------------------------------------------------------

    /**
     * Initie (ou réessaie) le versement d'une commande.
     * - payout COMPLETED/PAID existant → retourné tel quel (aucun doublon) ;
     * - payout PENDING/PROCESSING → réconcilié par polling, jamais recréé ;
     * - payout FAILED/CANCELLED → retry avec le MÊME payoutId (PawaPay
     *   répond DUPLICATE_IGNORED s'il l'avait déjà accepté).
     */
    public PayoutOutcome initiateOrRetry(int orderId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Map<String, Object> check = checkEligibility(orderId);
        if (!Boolean.TRUE.equals(check.get("eligible"))) {
            throw new AppException(ErrorCodes.PAYOUT_BLOCKED, 409,
                "Versement bloqué : " + check.get("message"));
        }
        SupplierPayment existing;
        try {
            existing = payoutDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) {
            String st = existing.getStatus();
            if (isOpenStatus(st)) {
                reconcile(existing);
                SupplierPayment fresh = reload(existing.getId());
                if (fresh != null) existing = fresh;
                st = existing.getStatus();
            }
            if ("COMPLETED".equals(st) || "PAID".equals(st)) {
                LOG.info(() -> "PawaPay payout already done: orderId=" + orderId);
                return new PayoutOutcome(existing, true);
            }
            if (isOpenStatus(st) || "BLOCKED".equals(st)) {
                return new PayoutOutcome(existing, true);
            }
            // FAILED / CANCELLED → retry contrôlé avec le MÊME payoutId.
            final SupplierPayment retryRow = existing;
            LOG.info(() -> "PawaPay payout retry: payoutId=" + retryRow.getPayoutId());
            callPawaPay(retryRow);
            return new PayoutOutcome(reload(retryRow.getId()), false);
        }

        int supplierId = (int) check.get("supplierId");
        int net = (int) check.get("net");
        String provider = String.valueOf(check.get("provider"));
        String phone = String.valueOf(check.get("phone"));
        String payoutId = newPayoutId();
        Order o = loadOrder(orderId);

        SupplierPayment sp = new SupplierPayment();
        sp.setSupplierId(supplierId);
        sp.setOrderId(orderId);
        sp.setReference("PO-" + o.getOrderNumber());
        sp.setAmount(net);
        sp.setStatus("PENDING");
        sp.setNotes("Versement auto " + o.getOrderNumber());
        sp.setPayoutId(payoutId);
        sp.setProvider(provider);
        sp.setPhoneNumber(phone);
        sp.setGrossAmount((int) check.get("gross"));
        sp.setCommission((int) check.get("commission"));
        sp.setPawapayFee((int) check.get("pawapayFee"));
        final int payoutRowId;
        try {
            payoutRowId = payoutDAO.createAuto(sp);
            sp.setId(payoutRowId);
        } catch (SQLException e) {
            // Course : une ligne créée entre-temps → la réutiliser.
            SupplierPayment raced = findByOrderQuiet(orderId);
            if (raced != null) return new PayoutOutcome(raced, true);
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LOG.info(() -> "PawaPay Payout created: payoutId=" + payoutId + " order=" + o.getOrderNumber());
        callPawaPay(sp);
        return new PayoutOutcome(reload(payoutRowId), false);
    }

    /** Appel réseau APRÈS persistance (payoutId connu même en cas de timeout). */
    private void callPawaPay(SupplierPayment sp) throws AppException {
        PawaPayClient client = new PawaPayClient();
        Order o;
        try {
            o = orderDAO.findById(sp.getOrderId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        java.util.List<Map<String, String>> metadata = new java.util.ArrayList<>();
        metadata.add(singleMeta("orderId", o != null ? o.getOrderNumber() : String.valueOf(sp.getOrderId())));
        metadata.add(singleMeta("channel", "BAZAAR_PAYOUT"));
        Map<String, Object> resp;
        try {
            resp = client.initiatePayout(sp.getPayoutId(), String.valueOf(sp.getAmount()),
                PawaPayConfig.CURRENCY, sp.getPhoneNumber(), sp.getProvider(),
                o != null ? o.getOrderNumber() : null, "BAZAAR PAYOUT", metadata);
        } catch (AppException e) {
            // Réseau/PawaPay injoignable : réconciliation immédiate best-effort
            // (NOT_FOUND + PENDING → FAILED, retry sûr même payoutId).
            // L'erreur d'origine remonte.
            try {
                SupplierPayment created = payoutDAO.findById(sp.getId());
                if (created != null) reconcile(created);
            } catch (Exception ignored) {}
            LOG.warning(() -> "PawaPay Payout processing (initiation failed): payoutId=" + sp.getPayoutId());
            throw e;
        }
        String status = str(resp.get("status"));
        try {
            if ("ACCEPTED".equals(status)) {
                // AVANT : ACCEPTED → PROCESSING (raccourci).
                // APRÈS : ACCEPTED stocké tel quel (fidélité doc), la
                // réconciliation fera avancer vers PROCESSING/COMPLETED.
                // POURQUOI : distinguer accepté / en file / soumis (§19-20).
                payoutDAO.updateAfterInitiation(sp.getId(), "ACCEPTED", null);
                sp.setStatus("ACCEPTED");
                LOG.info(() -> "PawaPay Payout accepted: payoutId=" + sp.getPayoutId());
            } else if ("DUPLICATE_IGNORED".equals(status)) {
                // PawaPay connaît déjà ce payoutId : apprendre son vrai état
                // au lieu de supposer PROCESSING.
                String learned = "PROCESSING";
                try {
                    Map<String, Object> remote = client.getPayout(sp.getPayoutId());
                    String polled = dataStatusOf(remote);
                    String mapped = localStatusFor(polled);
                    if (mapped != null) learned = mapped;
                } catch (Exception ignored) {}
                payoutDAO.updateAfterInitiation(sp.getId(), learned, null);
                sp.setStatus(learned);
                LOG.info(() -> "PawaPay Payout duplicate ignored: payoutId=" + sp.getPayoutId());
            } else {
                String reason = PawaPayPaymentService.failureOf(resp);
                payoutDAO.updateAfterInitiation(sp.getId(), "FAILED", reason);
                sp.setStatus("FAILED");
                sp.setFailureReason(reason);
                LOG.info(() -> "PawaPay Payout failed at initiation: payoutId=" + sp.getPayoutId());
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    private static Map<String, String> singleMeta(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    // ------------------------------------------------------------------
    // Statut / réconciliation / callback
    // ------------------------------------------------------------------

    /** Statut d'un versement par payoutId, réconcilié en base si final. */
    public Map<String, Object> getPayoutStatus(String payoutId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        if (payoutId == null || payoutId.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Identifiant de versement requis");
        }
        SupplierPayment sp;
        try {
            sp = payoutDAO.findByPayoutId(payoutId.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (sp == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Versement introuvable");
        }
        reconcile(sp);
        SupplierPayment fresh = reload(sp.getId());
        return maskedView(fresh != null ? fresh : sp);
    }

    /** Statut du versement d'une commande (réconcilié si non final). */
    public Map<String, Object> getOrderPayoutStatus(int orderId) throws AppException, SQLException {
        SupplierPayment sp;
        try {
            sp = payoutDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (sp == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun versement initié");
        }
        if (!isFinalStatus(sp.getStatus()) && PawaPayConfig.isConfigured()
                && sp.getPayoutId() != null) {
            final String logId = sp.getPayoutId();
            try {
                reconcile(sp);
                SupplierPayment fresh = reload(sp.getId());
                if (fresh != null) sp = fresh;
            } catch (AppException | SQLException e) {
                LOG.warning(() -> "PawaPay payout status check failed for " + logId);
            }
        }
        return maskedView(sp);
    }

    /**
     * Traite un callback PawaPay (doc : payoutId, status, amount, currency,
     * country, recipient, created, customerMessage, providerTransactionId,
     * failureReason, metadata — statuts COMPLETED / PROCESSING / FAILED).
     * Discipline : réconciliation serveur prime, journal idempotent
     * payment_events (clé payout:{id}:{statut}), transitions conditionnelles
     * uniquement. Un callback répété ne re-déclenche jamais rien.
     */
    public Map<String, Object> handleCallback(Map<String, Object> payload, String rawJson)
            throws AppException, SQLException {
        String payoutId = payload == null ? null : str(payload.get("payoutId"));
        String status = payload == null ? null : str(payload.get("status"));
        if (payoutId == null || payoutId.isEmpty() || status == null || status.isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Notification PawaPay inexploitable");
        }
        LOG.info(() -> "PawaPay payout callback received: payoutId=" + payoutId + " status=" + status);
        SupplierPayment sp;
        try {
            sp = payoutDAO.findByPayoutId(payoutId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payoutId", payoutId);
        if (sp == null) {
            out.put("success", true);
            out.put("known", false);
            return out;
        }
        // Réconciliation serveur : le polling prime sur le callback.
        // Sans réconciliation réussie, aucun basculement (anti-callback forgé) :
        // on diffère, PawaPay réessaiera et le polling convergera.
        String effectiveStatus = status;
        String providerTxId = str(payload.get("providerTransactionId"));
        if (PawaPayConfig.isConfigured()) {
            boolean reconciled = false;
            try {
                Map<String, Object> remote = new PawaPayClient().getPayout(payoutId);
                String polled = dataStatusOf(remote);
                if (polled != null) { effectiveStatus = polled; reconciled = true; }
                String remoteTx = dataProviderTxOf(remote);
                if (remoteTx != null) providerTxId = remoteTx;
            } catch (AppException e) {
                LOG.warning(() -> "PawaPay payout reconcile failed for " + payoutId + ", deferring callback");
            }
            if (!reconciled) {
                out.put("success", true);
                out.put("known", true);
                out.put("deferred", true);
                return out;
            }
        }
        out.put("success", true);
        out.put("known", true);
        // Journal idempotent : payout:{id}:{statutEffectif} (structure
        // payment_events réutilisée, rattachée au dépôt de la commande).
        String eventKey = "payout:" + payoutId + ":" + effectiveStatus;
        boolean isNewEvent = journalCallback(sp, eventKey, rawJson);
        if (!isNewEvent) {
            SupplierPayment fresh = reload(sp.getId());
            out.put("duplicate", true);
            out.put("transitioned", false);
            out.put("status", effectiveStatus);
            if (fresh != null) out.put("localStatus", fresh.getStatus());
            return out;
        }
        boolean transitioned = applyRemoteStatus(sp, effectiveStatus,
            "FAILED".equals(effectiveStatus) ? PawaPayPaymentService.failureOf(payload) : null,
            providerTxId);
        SupplierPayment fresh = reload(sp.getId());
        out.put("duplicate", false);
        out.put("transitioned", transitioned);
        out.put("status", effectiveStatus);
        if (fresh != null) out.put("localStatus", fresh.getStatus());
        return out;
    }

    /** Redemande le callback final d'un versement (réconciliation). */
    public Map<String, Object> resendCallback(String payoutId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        SupplierPayment sp;
        try {
            sp = payoutDAO.findByPayoutId(payoutId == null ? "" : payoutId.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (sp == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Versement introuvable");
        }
        Map<String, Object> resp = new PawaPayClient().resendPayoutCallback(sp.getPayoutId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("payoutId", sp.getPayoutId());
        out.put("status", resp.get("status"));
        if (resp.get("failureReason") != null) out.put("failureReason", resp.get("failureReason"));
        return out;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Applique un statut distant à la ligne locale (réconciliation). */
    private void reconcile(SupplierPayment sp) throws AppException, SQLException {
        if (sp.getPayoutId() == null || isFinalStatus(sp.getStatus())) return;
        Map<String, Object> remote = new PawaPayClient().getPayout(sp.getPayoutId());
        if (!"FOUND".equals(str(remote.get("status")))) {
            // PawaPay ne connaît pas ce payoutId : initiation jamais parvenue.
            // PENDING → FAILED (retry sûr avec le MÊME payoutId, jamais vu
            // par PawaPay). ACCEPTED/ENQUEUED/PROCESSING = ACCEPTED : on ne
            // touche à rien (transitoire).
            if ("NOT_FOUND".equals(str(remote.get("status"))) && "PENDING".equals(sp.getStatus())) {
                boolean moved;
                try {
                    moved = payoutDAO.transitionToFinal(sp.getId(), "FAILED",
                        "Demande non parvenue au prestataire", null);
                } catch (SQLException e) {
                    throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                        "Impossible de traiter votre demande.", e);
                }
                if (moved) {
                    LOG.info(() -> "PawaPay Payout marked failed (not found): payoutId=" + sp.getPayoutId());
                }
            }
            return;
        }
        String polled = dataStatusOf(remote);
        if (polled == null) return;
        String providerTxId = dataProviderTxOf(remote);
        try {
            if (isFinalStatus(polled)) {
                String reason = null;
                if ("FAILED".equals(polled)) {
                    Object d = remote.get("data");
                    if (d instanceof Map) reason = PawaPayPaymentService.failureOf((Map<String, Object>) d);
                }
                boolean moved = payoutDAO.transitionToFinal(sp.getId(), polled, reason, providerTxId);
                if (moved && "COMPLETED".equals(polled)) {
                    LOG.info(() -> "PawaPay Payout completed (poll): payoutId=" + sp.getPayoutId());
                    notifyCompleted(sp);
                }
            } else {
                String local = localStatusFor(polled);
                if (local != null && isTransitionAllowed(sp.getStatus(), local)) {
                    payoutDAO.transitionForward(sp.getId(), local, providerTxId);
                } else {
                    payoutDAO.touchReconciled(sp.getId(), providerTxId);
                }
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /**
     * Applique un statut distant effectif (callback déjà réconcilié) :
     * final → transitionToFinal (+ notifications), non-final →
     * transitionForward si autorisée. Retourne true si la ligne a bougé.
     */
    private boolean applyRemoteStatus(SupplierPayment sp, String effectiveStatus,
            String failureReason, String providerTransactionId) throws AppException {
        boolean moved;
        try {
            if (isFinalStatus(effectiveStatus)) {
                moved = payoutDAO.transitionToFinal(sp.getId(), effectiveStatus,
                    failureReason, providerTransactionId);
            } else {
                String local = localStatusFor(effectiveStatus);
                if (local != null && isTransitionAllowed(sp.getStatus(), local)) {
                    moved = payoutDAO.transitionForward(sp.getId(), local, providerTransactionId);
                } else {
                    payoutDAO.touchReconciled(sp.getId(), providerTransactionId);
                    moved = false;
                }
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        SupplierPayment fresh = reload(sp.getId());
        String from = sp.getStatus();
        String to = fresh == null ? from : fresh.getStatus();
        if (moved && "COMPLETED".equals(to)
                && ("COMPLETED".equals(effectiveStatus))) {
            LOG.info(() -> "PawaPay Payout completed: payoutId=" + sp.getPayoutId());
            notifyCompleted(fresh == null ? sp : fresh);
        } else if (moved) {
            LOG.info(() -> "PawaPay Payout " + from + " -> " + to + ": payoutId=" + sp.getPayoutId());
        }
        if (!from.equals(to) && ("CANCELLED".equals(from) || "BLOCKED".equals(from))
                && isFinalStatus(to)) {
            // Garde explicite §9 : la vérité mouvement-de-fonds prime sur
            // l'annulation locale (ex : cancel demandé mais fonds partis).
            LOG.warning(() -> "PawaPay Payout override " + from + " -> " + to
                + " (preuve serveur): payoutId=" + sp.getPayoutId());
        }
        return moved;
    }

    /**
     * Journal idempotent du callback (payment_events réutilisée).
     * Clé : payout:{payoutId}:{statut}. Rattachée au dépôt PawaPay de la
     * commande (à défaut : aucun journal, les transitions conditionnelles
     * protègent déjà). Retourne true si nouvel événement.
     */
    private boolean journalCallback(SupplierPayment sp, String eventKey, String rawJson) {
        try {
            Payment deposit = paymentDAO.findByOrderId(sp.getOrderId() == null ? -1 : sp.getOrderId());
            if (deposit == null) return true;
            return new com.ecommerce.dao.PaymentEventDAO()
                .insertIfNew(deposit.getId(), eventKey, rawJson);
        } catch (SQLException e) {
            return true;
        }
    }

    static boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "PAID".equals(status);
    }

    /** payoutId PawaPay : UUIDv4 unique, généré par BAZAAR avant l'appel. */
    static String newPayoutId() {
        return UUID.randomUUID().toString();
    }

    /** Statuts locaux non finaux (le payout peut encore bouger). */
    static boolean isOpenStatus(String status) {
        return "PENDING".equals(status) || "PROCESSING".equals(status)
            || "ACCEPTED".equals(status) || "ENQUEUED".equals(status);
    }

    /**
     * Statut distant PawaPay → statut local BAZAAR (noms doc, jamais
     * inventés). IN_RECONCILIATION n'a pas d'équivalent local : PROCESSING
     * (réconciliation en cours = traitement en cours).
     */
    static String localStatusFor(String remoteStatus) {
        if ("ACCEPTED".equals(remoteStatus)) return "ACCEPTED";
        if ("ENQUEUED".equals(remoteStatus)) return "ENQUEUED";
        if ("PROCESSING".equals(remoteStatus)) return "PROCESSING";
        if ("IN_RECONCILIATION".equals(remoteStatus)) return "PROCESSING";
        if ("COMPLETED".equals(remoteStatus)) return "COMPLETED";
        if ("FAILED".equals(remoteStatus)) return "FAILED";
        return null;
    }

    /**
     * Table de transitions non finales autorisées (pure, testée) :
     * - jamais en arrière (PROCESSING → ACCEPTED interdit) ;
     * - jamais depuis un statut terminal (COMPLETED/PAID/FAILED/
     *   CANCELLED/BLOCKED) ;
     * - les finales passent par transitionToFinal (avec garde explicite
     *   CANCELLED/BLOCKED → COMPLETED/FAILED sur preuve serveur).
     */
    static boolean isTransitionAllowed(String fromLocal, String toLocal) {
        if (fromLocal == null || toLocal == null) return false;
        if (!isOpenStatus(fromLocal) || !isOpenStatus(toLocal)) return false;
        if (fromLocal.equals(toLocal)) return true; // idempotent, touch only
        if ("PENDING".equals(fromLocal)) return true; // PENDING → tout état ouvert
        if ("ACCEPTED".equals(fromLocal)) {
            return "ENQUEUED".equals(toLocal) || "PROCESSING".equals(toLocal);
        }
        if ("ENQUEUED".equals(fromLocal)) {
            return "ENQUEUED".equals(toLocal) || "PROCESSING".equals(toLocal);
        }
        // PROCESSING : seul PROCESSING (touch).
        return false;
    }

    public static boolean isPayoutProvider(String provider) {
        return "MTN_MOMO_CMR".equals(provider) || "ORANGE_CMR".equals(provider);
    }

    /**
     * Numéro fournisseur correspondant à l'opérateur du dépôt (normalisé,
     * jamais en clair dans les logs). MTN_MOMO_CMR → mtn_phone,
     * ORANGE_CMR → orange_phone. Null si absent/invalide.
     */
    public static String resolveSupplierPhone(com.ecommerce.model.Supplier sup, String provider) {
        if (sup == null || !isPayoutProvider(provider)) return null;
        String raw = "MTN_MOMO_CMR".equals(provider) ? sup.getMtnPhone() : sup.getOrangePhone();
        return PawaPayPaymentService.normalizeMsisdn(raw);
    }

    @SuppressWarnings("unchecked")
    private static String dataStatusOf(Map<String, Object> remote) {
        if (remote == null) return null;
        Object d = remote.get("data");
        if (!(d instanceof Map)) return null;
        return str(((Map<String, Object>) d).get("status"));
    }

    /** Référence assignée par le provider (reçu SMS, callback). */
    @SuppressWarnings("unchecked")
    static String dataProviderTxOf(Map<String, Object> remote) {
        if (remote == null) return null;
        Object d = remote.get("data");
        if (!(d instanceof Map)) return null;
        Object tx = ((Map<String, Object>) d).get("providerTransactionId");
        return tx == null ? null : String.valueOf(tx);
    }

    // ------------------------------------------------------------------
    // Cancel enqueued payout (POST /v2/payouts/fail-enqueued/{id})
    // ------------------------------------------------------------------

    /**
     * Annule un versement ENQUEUED côté PawaPay (doc : → FAILED /
     * MANUALLY_CANCELLED + callback). Garde stricte AVANT l'appel :
     * - ligne locale finale (COMPLETED/PAID/FAILED) ou CANCELLED/BLOCKED
     *   → refusé (jamais de faux cancel sur de l'argent parti) ;
     * - statut distant ≠ ENQUEUED → refusé INVALID_STATE (local réconcilié).
     */
    public Map<String, Object> cancelEnqueued(int orderId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        SupplierPayment sp;
        try {
            sp = payoutDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (sp == null || sp.getPayoutId() == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun versement à annuler");
        }
        if (!isOpenStatus(sp.getStatus())) {
            throw new AppException(ErrorCodes.PAYOUT_CANCEL_REFUSED, 409,
                "Annulation impossible : versement " + sp.getStatus());
        }
        Map<String, Object> remote = new PawaPayClient().getPayout(sp.getPayoutId());
        if (!"FOUND".equals(str(remote.get("status")))) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404,
                "Versement inconnu du prestataire");
        }
        String polled = dataStatusOf(remote);
        if (!"ENQUEUED".equals(polled)) {
            // Plus annulable (déjà soumis/terminé) : aligner le local.
            reconcile(sp);
            throw new AppException(ErrorCodes.PAYOUT_CANCEL_REFUSED, 409,
                "Annulation impossible : statut distant " + polled);
        }
        Map<String, Object> resp = new PawaPayClient().cancelEnqueuedPayout(sp.getPayoutId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payoutId", sp.getPayoutId());
        if ("ACCEPTED".equals(str(resp.get("status")))) {
            try {
                payoutDAO.transitionToFinal(sp.getId(), "FAILED", "MANUALLY_CANCELLED",
                    dataProviderTxOf(remote));
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                    "Impossible de traiter votre demande.", e);
            }
            LOG.info(() -> "PawaPay Payout cancelled (enqueued): payoutId=" + sp.getPayoutId());
            out.put("success", true);
            out.put("status", "FAILED");
            out.put("failureCode", "MANUALLY_CANCELLED");
            return out;
        }
        reconcile(sp);
        throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
            "Annulation refusée par le prestataire");
    }

    /**
     * Tentative d'annulation best-effort (litige urgent sur payout ENQUEUED).
     * Ne lève jamais : la protection immédiate reste l'annulation locale.
     * Retourne true si PawaPay a accepté l'annulation.
     */
    public boolean tryCancelEnqueuedForDispute(int orderId) {
        try {
            SupplierPayment sp = payoutDAO.findByOrderId(orderId);
            if (sp == null || !"ENQUEUED".equals(sp.getStatus())) return false;
            cancelEnqueued(orderId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Bulk payouts (POST /v2/payouts/bulk, max 20)
    // ------------------------------------------------------------------

    /** Résultat bulk : un résultat PAR commande (succès ou motif). */
    public static class BulkItemResult {
        public final int orderId;
        public final String orderNumber;
        public final String payoutId;
        public final String status;
        public final boolean duplicate;
        public final String error;

        BulkItemResult(int orderId, String orderNumber, String payoutId,
                String status, boolean duplicate, String error) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.payoutId = payoutId;
            this.status = status;
            this.duplicate = duplicate;
            this.error = error;
        }
    }

    /**
     * Versements groupés admin (fonctionnalité COMPLÉMENTAIRE au payout
     * individuel : N fournisseurs à payer en un traitement).
     * - chaque commande est validée (éligibilité) séparément ;
     * - chaque payout a son payoutId unique persisté AVANT l'appel ;
     * - UN SEUL appel POST /v2/payouts/bulk (jamais une boucle de POST) ;
     * - résultats appariés par payoutId, erreurs individuelles conservées ;
     * - callbacks finaux traités séparément par payout (route existante).
     */
    public java.util.List<BulkItemResult> initiateBulk(java.util.List<Integer> orderIds)
            throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        if (orderIds == null || orderIds.isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Aucune commande fournie");
        }
        if (orderIds.size() > PawaPayClient.BULK_MAX_ITEMS) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Bulk limité à " + PawaPayClient.BULK_MAX_ITEMS + " commandes par appel");
        }
        java.util.List<SupplierPayment> rows = new java.util.ArrayList<>();
        java.util.List<BulkItemResult> skipped = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Integer orderId : orderIds) {
            if (orderId == null || orderId <= 0 || !seen.add(orderId)) continue;
            // Isolation par commande : une commande en échec (introuvable,
            // inéligible) n'annule jamais le reste du bulk.
            BulkItemResult ready;
            try {
                ready = prepareBulkRow(orderId, rows);
            } catch (AppException ae) {
                ready = new BulkItemResult(orderId, null, null, null, false,
                    ae.getCode() + " : " + ae.getMessage());
            }
            if (ready != null) skipped.add(ready);
        }
        java.util.List<BulkItemResult> out = new java.util.ArrayList<>(skipped);
        if (rows.isEmpty()) return out;
        // UN SEUL appel bulk : corps = tableau de PayoutInitiationRequest.
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (SupplierPayment sp : rows) {
            Order o = loadOrder(sp.getOrderId());
            java.util.List<Map<String, String>> metadata = new java.util.ArrayList<>();
            metadata.add(singleMeta("orderId", o.getOrderNumber()));
            metadata.add(singleMeta("channel", "BAZAAR_PAYOUT_BULK"));
            items.add(PawaPayClient.buildPayoutBody(sp.getPayoutId(),
                String.valueOf(sp.getAmount()), PawaPayConfig.CURRENCY,
                sp.getPhoneNumber(), sp.getProvider(), o.getOrderNumber(),
                "BAZAAR PAYOUT", metadata));
        }
        java.util.List<Map<String, Object>> responses;
        try {
            responses = new PawaPayClient().initiateBulkPayouts(items);
        } catch (AppException e) {
            // Transport en panne : les lignes restent PENDING, chacune sera
            // réconciliée par polling (payoutIds déjà stockés).
            LOG.warning(() -> "PawaPay Bulk processing (initiation failed): items=" + items.size());
            for (SupplierPayment sp : rows) {
                out.add(new BulkItemResult(sp.getOrderId(), null, sp.getPayoutId(),
                    sp.getStatus(), false, "Prestataire injoignable (à réconcilier)"));
            }
            return out;
        }
        Map<String, Map<String, Object>> byPayoutId = mapBulkDecisions(responses);
        for (SupplierPayment sp : rows) {
            out.add(applyBulkDecision(sp, byPayoutId.get(sp.getPayoutId())));
        }
        return out;
    }

    /**
     * Prépare UNE ligne bulk : éligibilité + persistance PENDING AVANT
     * l'appel. Retourne null si la ligne est prête (ajoutée à rows),
     * sinon le résultat d'exclusion (inéligible / déjà terminé).
     */
    private BulkItemResult prepareBulkRow(int orderId, java.util.List<SupplierPayment> rows)
            throws AppException, SQLException {
        Map<String, Object> check = checkEligibility(orderId);
        if (!Boolean.TRUE.equals(check.get("eligible"))) {
            return new BulkItemResult(orderId, String.valueOf(check.get("orderNumber")),
                null, null, false, "Bloqué : " + check.get("message"));
        }
        SupplierPayment existing;
        try {
            existing = payoutDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) {
            String st = existing.getStatus();
            if ("COMPLETED".equals(st) || "PAID".equals(st)) {
                return new BulkItemResult(orderId, null, existing.getPayoutId(),
                    st, true, null);
            }
            if (isOpenStatus(st)) {
                return new BulkItemResult(orderId, null, existing.getPayoutId(),
                    st, true, "Versement déjà en cours (réconciliation par polling)");
            }
            // FAILED / CANCELLED : réutiliser la ligne (même payoutId).
            rows.add(existing);
            return null;
        }
        int supplierId = (int) check.get("supplierId");
        int net = (int) check.get("net");
        String provider = String.valueOf(check.get("provider"));
        String phone = String.valueOf(check.get("phone"));
        Order o = loadOrder(orderId);
        SupplierPayment sp = new SupplierPayment();
        sp.setSupplierId(supplierId);
        sp.setOrderId(orderId);
        sp.setReference("PO-" + o.getOrderNumber());
        sp.setAmount(net);
        sp.setStatus("PENDING");
        sp.setNotes("Versement bulk " + o.getOrderNumber());
        sp.setPayoutId(newPayoutId());
        sp.setProvider(provider);
        sp.setPhoneNumber(phone);
        sp.setGrossAmount((int) check.get("gross"));
        sp.setCommission((int) check.get("commission"));
        sp.setPawapayFee((int) check.get("pawapayFee"));
        try {
            sp.setId(payoutDAO.createAuto(sp));
        } catch (SQLException e) {
            SupplierPayment raced = findByOrderQuiet(orderId);
            if (raced != null) {
                return new BulkItemResult(orderId, null, raced.getPayoutId(),
                    raced.getStatus(), true, null);
            }
            return new BulkItemResult(orderId, o.getOrderNumber(), null,
                null, false, "Impossible de traiter votre demande.");
        }
        rows.add(sp);
        return null;
    }

    /**
     * Appariement pur des réponses bulk par payoutId (jamais par index :
     * l'ordre de réponse n'est pas garanti). Testable sans base.
     */
    static Map<String, Map<String, Object>> mapBulkDecisions(
            java.util.List<Map<String, Object>> responses) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (responses == null) return out;
        for (Map<String, Object> r : responses) {
            if (r == null) continue;
            Object id = r.get("payoutId");
            if (id != null) out.put(String.valueOf(id), r);
        }
        return out;
    }

    /** Applique UNE décision bulk à sa ligne (ACCEPTED/DUPLICATE/REJECTED). */
    private BulkItemResult applyBulkDecision(SupplierPayment sp, Map<String, Object> resp)
            throws AppException {
        Order o = loadOrderQuiet(sp.getOrderId());
        String orderNumber = o == null ? null : o.getOrderNumber();
        if (resp == null) {
            return new BulkItemResult(sp.getOrderId(), orderNumber, sp.getPayoutId(),
                "PENDING", false, "Sans réponse PawaPay (à réconcilier)");
        }
        String status = str(resp.get("status"));
        try {
            if ("ACCEPTED".equals(status)) {
                payoutDAO.updateAfterInitiation(sp.getId(), "ACCEPTED", null);
                return new BulkItemResult(sp.getOrderId(), orderNumber, sp.getPayoutId(),
                    "ACCEPTED", false, null);
            }
            if ("DUPLICATE_IGNORED".equals(status)) {
                String learned = "PROCESSING";
                try {
                    Map<String, Object> remote = new PawaPayClient().getPayout(sp.getPayoutId());
                    String mapped = localStatusFor(dataStatusOf(remote));
                    if (mapped != null) learned = mapped;
                } catch (Exception ignored) {}
                payoutDAO.updateAfterInitiation(sp.getId(), learned, null);
                return new BulkItemResult(sp.getOrderId(), orderNumber, sp.getPayoutId(),
                    learned, true, null);
            }
            String reason = PawaPayPaymentService.failureOf(resp);
            payoutDAO.updateAfterInitiation(sp.getId(), "FAILED", reason);
            return new BulkItemResult(sp.getOrderId(), orderNumber, sp.getPayoutId(),
                "FAILED", false, reason);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    private Order loadOrderQuiet(int orderId) {
        try {
            return orderDAO.findById(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    private Order loadOrder(int orderId) throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    private Supplier loadSupplier(int supplierId) throws AppException {
        try {
            return supplierDAO.findById(supplierId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    private LedgerEntry saleEntry(int orderId) throws AppException {
        List<LedgerEntry> entries;
        try {
            entries = monetizationDAO.findByOrder(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        for (LedgerEntry e : entries) {
            if ("SALE".equals(e.getKind()) && "EARNED".equals(e.getStatus())) return e;
        }
        return null;
    }

    private Payment completedDeposit(int orderId) throws AppException {
        try {
            return paymentDAO.findCompletedPawaPayByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    private SupplierPayment reload(int id) {
        try {
            return payoutDAO.findById(id);
        } catch (SQLException e) {
            return null;
        }
    }

    private SupplierPayment findByOrderQuiet(int orderId) {
        try {
            return payoutDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    /** Vue JSON sans donnée sensible (téléphone masqué, jamais de token). */
    public static Map<String, Object> maskedView(SupplierPayment sp) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (sp == null) return m;
        m.put("id", sp.getId());
        m.put("orderId", sp.getOrderId());
        m.put("supplierId", sp.getSupplierId());
        m.put("reference", sp.getReference());
        m.put("payoutId", sp.getPayoutId());
        m.put("provider", sp.getProvider());
        m.put("amount", sp.getAmount());
        m.put("grossAmount", sp.getGrossAmount());
        m.put("commission", sp.getCommission());
        m.put("pawapayFee", sp.getPawapayFee());
        m.put("currency", PawaPayConfig.CURRENCY);
        m.put("status", sp.getStatus());
        m.put("providerTransactionId", sp.getProviderTransactionId());
        m.put("phoneMasked", PawaPayPaymentService.maskPhone(sp.getPhoneNumber()));
        m.put("failureReason", sp.getFailureReason());
        m.put("lastReconciledAt", sp.getLastReconciledAt());
        m.put("createdAt", sp.getCreatedAt());
        return m;
    }

    private void notifyCompleted(SupplierPayment sp) {
        try {
            com.ecommerce.service.NotificationService notifs = new com.ecommerce.service.NotificationService();
            notifs.notifySupplier(sp.getSupplierId(), "PAYOUT_COMPLETED", "Versement reçu",
                "Votre versement " + sp.getReference() + " (" + sp.getAmount() + " FCFA) a été confirmé.",
                "PAYOUT", sp.getId(), null, "PAYOUT:" + sp.getId() + ":COMPLETED");
        } catch (Exception ignored) {}
        try {
            Supplier sup = supplierDAO.findById(sp.getSupplierId());
            if (sup != null && sup.getEmail() != null
                    && com.ecommerce.service.EmailService.isValidEmail(sup.getEmail())) {
                new com.ecommerce.service.EmailService().queueAndSend(sup.getEmail(),
                    "Versement BAZAAR confirmé — " + sp.getReference(),
                    "Bonjour " + (sup.getName() == null ? "" : sup.getName()) + ",<br>"
                        + "Votre versement <b>" + sp.getReference() + "</b> de <b>"
                        + sp.getAmount() + " FCFA</b> a été confirmé.",
                    "PAYOUT_COMPLETED", sp.getId());
            }
        } catch (Exception ignored) {}
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
