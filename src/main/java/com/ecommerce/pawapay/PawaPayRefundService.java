package com.ecommerce.pawapay;

import com.ecommerce.dao.DisputeDAO;
import com.ecommerce.dao.MonetizationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.PayoutDAO;
import com.ecommerce.dao.RefundDAO;
import com.ecommerce.model.Dispute;
import com.ecommerce.model.LedgerEntry;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Refund;
import com.ecommerce.model.SupplierPayment;
import com.ecommerce.service.MonetizationService;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Remboursements PawaPay (POST /v2/refunds {refundId, depositId}).
 *
 * Cycle : litige NOT_RECEIVED (OPEN) → décision admin → REFUND_PENDING →
 * REFUND_PROCESSING → REFUNDED (COMPLETED confirmé par PawaPay uniquement).
 * Jamais de "REFUNDED" affiché sans confirmation PawaPay.
 *
 * Effets à COMPLETED confirmé : dépôt → REFUNDED, contre-passation
 * comptable (commission = 0 net), litige → RESOLVED_REFUNDED, payout
 * ouvert annulé, client notifié. Idempotence stricte (1 refund actif
 * par commande, retry = même refundId).
 */
public class PawaPayRefundService {

    private static final Logger LOG = Logger.getLogger(PawaPayRefundService.class.getName());

    /** Résultat : ligne de refund + flag "existait déjà" (retry sûr). */
    public static class RefundOutcome {
        public final Refund refund;
        public final boolean duplicate;

        RefundOutcome(Refund refund, boolean duplicate) {
            this.refund = refund;
            this.duplicate = duplicate;
        }
    }

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final RefundDAO refundDAO = new RefundDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final DisputeDAO disputeDAO = new DisputeDAO();

    // ------------------------------------------------------------------
    // Initiation / retry (idempotents, acte admin)
    // ------------------------------------------------------------------

    /**
     * Initie (ou réessaie) le remboursement TOTAL d'une commande.
     * Exige un dépôt PawaPay COMPLETED. Montant = dépôt, jamais frontend.
     * Garde : requested ≤ deposit − déjà-remboursé (full-only aujourd'hui).
     * Un refund non final existant est retourné tel quel ; un refund
     * COMPLETED → ALREADY_REFUNDED.
     */
    public RefundOutcome initiateOrRetry(int orderId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Order o = loadOrder(orderId);
        Payment deposit = completedDeposit(orderId);
        if (deposit == null) {
            throw new AppException(ErrorCodes.REFUND_NOT_ALLOWED, 409,
                "Aucun dépôt PawaPay confirmé à rembourser");
        }
        assertRefundable(deposit, deposit.getAmount());
        Refund existing;
        try {
            existing = refundDAO.findLatestByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) {
            String st = existing.getStatus();
            if ("COMPLETED".equals(st)) {
                throw new AppException(ErrorCodes.ALREADY_REFUNDED, 409, "Commande déjà remboursée");
            }
            if (isOpenStatus(st)) {
                reconcile(existing);
                Refund fresh = reload(existing.getId());
                if (fresh != null) existing = fresh;
                st = existing.getStatus();
                if ("COMPLETED".equals(st)) {
                    throw new AppException(ErrorCodes.ALREADY_REFUNDED, 409, "Commande déjà remboursée");
                }
                if (isOpenStatus(st)) {
                    return new RefundOutcome(existing, true);
                }
            }
            // FAILED → retry contrôlé avec le MÊME refundId.
            final Refund retryRow = existing;
            LOG.info(() -> "PawaPay refund retry: refundId=" + retryRow.getRefundId());
            callPawaPay(retryRow);
            return new RefundOutcome(reload(retryRow.getId()), false);
        }

        Refund r = new Refund();
        r.setOrderId(orderId);
        r.setPaymentId(deposit.getId());
        r.setRefundId(newRefundId());
        r.setDepositId(deposit.getProviderTransactionId());
        r.setAmount(deposit.getAmount());
        r.setCurrency(PawaPayConfig.CURRENCY);
        r.setStatus("PENDING");
        final int refundRowId;
        try {
            refundRowId = refundDAO.create(r);
            r.setId(refundRowId);
        } catch (SQLException e) {
            // Course check-then-insert : garde UNIQUE uq_refund_open →
            // retourner le refund ouvert existant (AUCUN 2ᵉ appel PawaPay).
            Refund raced = findOpenQuiet(orderId);
            if (raced != null) return new RefundOutcome(raced, true);
            raced = findLatestQuiet(orderId);
            if (raced != null) return new RefundOutcome(raced, true);
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LOG.info(() -> "PawaPay Refund created: refundId=" + r.getRefundId()
            + " order=" + o.getOrderNumber());
        callPawaPay(r);
        return new RefundOutcome(reload(refundRowId), false);
    }

    /** Appel réseau APRÈS persistance (refundId connu même en cas de timeout). */
    private void callPawaPay(Refund r) throws AppException {
        PawaPayClient client = new PawaPayClient();
        Order o = null;
        try {
            o = orderDAO.findById(r.getOrderId());
        } catch (SQLException ignored) {}
        Map<String, Object> resp;
        try {
            // Remboursement TOTAL : amount = montant du dépôt (exigé par le
            // schéma officiel PaymentInitiationRequest) + metadata de
            // réconciliation (jamais de secret, jamais de PII).
            java.util.List<Map<String, String>> metadata = new java.util.ArrayList<>();
            metadata.add(singleMeta("orderId", o != null ? o.getOrderNumber() : String.valueOf(r.getOrderId())));
            metadata.add(singleMeta("channel", "BAZAAR_REFUND"));
            resp = client.initiateRefund(r.getRefundId(), r.getDepositId(),
                String.valueOf(r.getAmount()), r.getCurrency(),
                o != null ? o.getOrderNumber() : null, metadata);
        } catch (AppException e) {
            // Réconciliation immédiate best-effort (NOT_FOUND + PENDING →
            // FAILED, retry sûr même refundId). L'erreur d'origine remonte.
            try {
                Refund created = refundDAO.findById(r.getId());
                if (created != null) reconcile(created);
            } catch (Exception ignored) {}
            LOG.warning(() -> "PawaPay Refund processing (initiation failed): refundId=" + r.getRefundId());
            throw e;
        }
        String status = str(resp.get("status"));
        try {
            if ("ACCEPTED".equals(status)) {
                // AVANT : ACCEPTED → PROCESSING (raccourci).
                // APRÈS : ACCEPTED stocké tel quel (fidélité doc), la
                // réconciliation avance vers ENQUEUED/PROCESSING/COMPLETED.
                refundDAO.updateAfterInitiation(r.getId(), "ACCEPTED", null);
                r.setStatus("ACCEPTED");
                LOG.info(() -> "PawaPay Refund accepted: refundId=" + r.getRefundId());
            } else if ("DUPLICATE_IGNORED".equals(status)) {
                // PawaPay connaît déjà ce refundId : apprendre son vrai état.
                String learned = "PROCESSING";
                try {
                    Map<String, Object> remote = client.getRefund(r.getRefundId());
                    String mapped = localStatusFor(dataStatusOf(remote));
                    if (mapped != null) learned = mapped;
                } catch (Exception ignored) {}
                refundDAO.updateAfterInitiation(r.getId(), learned, null);
                r.setStatus(learned);
                LOG.info(() -> "PawaPay Refund duplicate ignored: refundId=" + r.getRefundId());
            } else {
                String reason = PawaPayPaymentService.failureOf(resp);
                refundDAO.updateAfterInitiation(r.getId(), "FAILED", reason);
                r.setStatus("FAILED");
                r.setFailureReason(reason);
                LOG.info(() -> "PawaPay Refund failed at initiation: refundId=" + r.getRefundId());
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

    public Map<String, Object> getRefundStatus(String refundId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        if (refundId == null || refundId.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Identifiant de remboursement requis");
        }
        Refund r;
        try {
            r = refundDAO.findByRefundId(refundId.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (r == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Remboursement introuvable");
        }
        reconcile(r);
        Refund fresh = reload(r.getId());
        return maskedView(fresh != null ? fresh : r);
    }

    public Map<String, Object> getOrderRefundStatus(int orderId) throws AppException, SQLException {
        Refund r;
        try {
            r = refundDAO.findLatestByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (r == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun remboursement initié");
        }
        if (!isFinalStatus(r.getStatus()) && PawaPayConfig.isConfigured()) {
            final String logId = r.getRefundId();
            try {
                reconcile(r);
                Refund fresh = reload(r.getId());
                if (fresh != null) r = fresh;
            } catch (AppException | SQLException e) {
                LOG.warning(() -> "PawaPay refund status check failed for " + logId);
            }
        }
        return maskedView(r);
    }

    /**
     * Traite un callback PawaPay ({refundId, status, ...}).
     * Idempotent : transitions conditionnelles uniquement.
     */
    public Map<String, Object> handleCallback(Map<String, Object> payload, String rawJson)
            throws AppException, SQLException {
        String refundId = payload == null ? null : str(payload.get("refundId"));
        String status = payload == null ? null : str(payload.get("status"));
        if (refundId == null || refundId.isEmpty() || status == null || status.isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Notification PawaPay inexploitable");
        }
        LOG.info(() -> "PawaPay refund callback received: refundId=" + refundId + " status=" + status);
        Refund r;
        try {
            r = refundDAO.findByRefundId(refundId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refundId", refundId);
        if (r == null) {
            out.put("success", true);
            out.put("known", false);
            return out;
        }
        // Réconciliation serveur : le polling prime sur le callback.
        // Sans réconciliation réussie, aucun basculement (anti-callback forgé).
        String effectiveStatus = status;
        String providerTxId = str(payload.get("providerTransactionId"));
        if (PawaPayConfig.isConfigured()) {
            boolean reconciled = false;
            try {
                Map<String, Object> remote = new PawaPayClient().getRefund(refundId);
                String polled = dataStatusOf(remote);
                if (polled != null) { effectiveStatus = polled; reconciled = true; }
                String remoteTx = dataProviderTxOf(remote);
                if (remoteTx != null) providerTxId = remoteTx;
            } catch (AppException e) {
                LOG.warning(() -> "PawaPay refund reconcile failed for " + refundId + ", deferring callback");
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
        // Journal idempotent : refund:{id}:{statut} (payment_events réutilisée).
        String eventKey = "refund:" + refundId + ":" + effectiveStatus;
        if (!journalCallback(r, eventKey, rawJson)) {
            Refund fresh = reload(r.getId());
            out.put("duplicate", true);
            out.put("transitioned", false);
            out.put("status", effectiveStatus);
            if (fresh != null) out.put("localStatus", fresh.getStatus());
            return out;
        }
        boolean transitioned = false;
        if (isFinalStatus(effectiveStatus)) {
            String reason = "FAILED".equals(effectiveStatus)
                ? PawaPayPaymentService.failureOf(payload) : null;
            try {
                transitioned = refundDAO.transitionToFinal(r.getId(), effectiveStatus, reason, providerTxId);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (transitioned && "COMPLETED".equals(effectiveStatus)) {
                LOG.info(() -> "PawaPay Refund completed: refundId=" + refundId);
                onRefundCompleted(r);
            } else if (transitioned) {
                LOG.info(() -> "PawaPay Refund failed: refundId=" + refundId);
            }
        } else {
            try {
                refundDAO.touchReconciled(r.getId(), providerTxId);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        Refund fresh = reload(r.getId());
        out.put("duplicate", false);
        out.put("transitioned", transitioned);
        out.put("status", effectiveStatus);
        if (fresh != null) out.put("localStatus", fresh.getStatus());
        return out;
    }

    /** Redemande le callback final d'un remboursement (réconciliation). */
    public Map<String, Object> resendCallback(String refundId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Refund r;
        try {
            r = refundDAO.findByRefundId(refundId == null ? "" : refundId.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (r == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Remboursement introuvable");
        }
        Map<String, Object> resp = new PawaPayClient().resendRefundCallback(r.getRefundId());
        // Le resend ne crée/modifie AUCUNE opération : on horodate juste la
        // réconciliation (traçabilité supervision).
        try {
            refundDAO.touchReconciled(r.getId(), null);
        } catch (SQLException ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("refundId", r.getRefundId());
        out.put("status", resp.get("status"));
        if (resp.get("failureReason") != null) out.put("failureReason", resp.get("failureReason"));
        return out;
    }

    // ------------------------------------------------------------------
    // Cancel enqueued refund (POST /v2/refunds/fail-enqueued/{id})
    // ------------------------------------------------------------------

    /**
     * Annule un remboursement ENQUEUED côté PawaPay (doc : → FAILED /
     * MANUALLY_CANCELLED + callback). Garde stricte AVANT l'appel :
     * - ligne locale finale (COMPLETED/FAILED) → refusé (jamais de faux
     *   cancel sur de l'argent rendu) ;
     * - statut distant ≠ ENQUEUED → refusé INVALID_STATE (local réconcilié).
     * En cas d'échec distant : trace locale conservée (statut inchangé),
     * réponse journalisée, réconciliation toujours possible.
     */
    public Map<String, Object> cancelEnqueued(int orderId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Refund r;
        try {
            r = refundDAO.findLatestByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (r == null || r.getRefundId() == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun remboursement à annuler");
        }
        if (!isOpenStatus(r.getStatus())) {
            throw new AppException(ErrorCodes.REFUND_CANCEL_REFUSED, 409,
                "Annulation impossible : remboursement " + r.getStatus());
        }
        Map<String, Object> remote = new PawaPayClient().getRefund(r.getRefundId());
        if (!"FOUND".equals(str(remote.get("status")))) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404,
                "Remboursement inconnu du prestataire");
        }
        String polled = dataStatusOf(remote);
        if (!"ENQUEUED".equals(polled)) {
            // Plus annulable : aligner le local sur la vérité serveur.
            reconcile(r);
            throw new AppException(ErrorCodes.REFUND_CANCEL_REFUSED, 409,
                "Annulation impossible : statut distant " + polled);
        }
        Map<String, Object> resp = new PawaPayClient().cancelEnqueuedRefund(r.getRefundId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refundId", r.getRefundId());
        if ("ACCEPTED".equals(str(resp.get("status")))) {
            try {
                refundDAO.transitionToFinal(r.getId(), "FAILED", "MANUALLY_CANCELLED",
                    dataProviderTxOf(remote));
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                    "Impossible de traiter votre demande.", e);
            }
            LOG.info(() -> "PawaPay Refund cancelled (enqueued): refundId=" + r.getRefundId());
            out.put("success", true);
            out.put("status", "FAILED");
            out.put("failureCode", "MANUALLY_CANCELLED");
            return out;
        }
        reconcile(r);
        throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 502,
            "Annulation refusée par le prestataire");
    }

    // ------------------------------------------------------------------
    // Effets métier du remboursement confirmé
    // ------------------------------------------------------------------

    /**
     * COMPLETED confirmé par PawaPay : dépôt → REFUNDED, contre-passation
     * (commission nette = 0), litige → RESOLVED_REFUNDED, payout ouvert
     * annulé (reste 0 FCFA), client notifié.
     */
    private void onRefundCompleted(Refund r) {
        try {
            paymentDAO.markRefundedIfCompleted(r.getPaymentId());
        } catch (SQLException ignored) {}
        try {
            new MonetizationService().reverseOrder(r.getOrderId(), "ADMIN_REFUND");
        } catch (Exception ignored) {}
        try {
            Dispute d = disputeDAO.findByOrderId(r.getOrderId());
            if (d != null && Dispute.STATUS_OPEN.equals(d.getStatus())) {
                disputeDAO.resolve(r.getOrderId(), Dispute.STATUS_REFUNDED,
                    "Remboursement PawaPay confirmé (" + r.getRefundId() + ").");
            }
        } catch (SQLException ignored) {}
        boolean payoutAlreadySent = false;
        try {
            SupplierPayment sp = payoutDAO.findByOrderId(r.getOrderId());
            if (sp != null) {
                String st = sp.getStatus();
                if ("COMPLETED".equals(st) || "PAID".equals(st)) {
                    // Argent déjà parti chez le fournisseur : recouvrement
                    // manuel requis (tracé, jamais silencieux).
                    payoutAlreadySent = true;
                    LOG.warning(() -> "Refund completed but payout already sent: orderId="
                        + r.getOrderId() + " payoutId=" + sp.getPayoutId());
                } else {
                    payoutDAO.cancelIfOpen(sp.getId(), "Remboursement confirmé");
                }
            }
        } catch (SQLException ignored) {}
        notifyClient(r, payoutAlreadySent);
    }

    private void notifyClient(Refund r, boolean payoutAlreadySent) {
        Order o;
        try {
            o = orderDAO.findById(r.getOrderId());
        } catch (SQLException e) {
            return;
        }
        if (o == null) return;
        try {
            com.ecommerce.service.NotificationService notifs = new com.ecommerce.service.NotificationService();
            if (o.getSupplierId() > 0) {
                notifs.notifySupplier(o.getSupplierId(), "PAYMENT_REFUNDED", "Commande remboursée",
                    "La commande " + o.getOrderNumber() + " a été remboursée ("
                        + r.getAmount() + " FCFA)."
                        + (payoutAlreadySent ? " Versement déjà envoyé : recouvrement manuel requis." : ""),
                    "REFUND", r.getId(), null,
                    "ORDER:" + o.getId() + ":REFUNDED:" + r.getId());
            }
        } catch (Exception ignored) {}
        try {
            if (o.getEmail() != null
                    && com.ecommerce.service.EmailService.isValidEmail(o.getEmail())) {
                new com.ecommerce.service.EmailService().queueAndSend(o.getEmail(),
                    com.ecommerce.service.EmailService.refundSubject(o.getOrderNumber()),
                    com.ecommerce.service.EmailService.refundBody(o.getFullname(), o.getOrderNumber())
                        + "<br>Montant : <b>" + r.getAmount() + " FCFA</b>"
                        + "<br>Référence : " + r.getRefundId(),
                    "PAYMENT_REFUNDED", o.getId());
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void reconcile(Refund r) throws AppException, SQLException {
        if (isFinalStatus(r.getStatus())) return;
        Map<String, Object> remote = new PawaPayClient().getRefund(r.getRefundId());
        if (!"FOUND".equals(str(remote.get("status")))) {
            // PawaPay ne connaît pas ce refundId : initiation jamais parvenue.
            // PENDING → FAILED (retry sûr avec le MÊME refundId).
            if ("NOT_FOUND".equals(str(remote.get("status"))) && "PENDING".equals(r.getStatus())) {
                boolean moved;
                try {
                    moved = refundDAO.transitionToFinal(r.getId(), "FAILED",
                        "Demande non parvenue au prestataire");
                } catch (SQLException e) {
                    throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                        "Impossible de traiter votre demande.", e);
                }
                if (moved) {
                    LOG.info(() -> "PawaPay Refund marked failed (not found): refundId=" + r.getRefundId());
                }
            }
            return;
        }
        String polled = dataStatusOf(remote);
        if (polled == null) return;
        if (!isFinalStatus(polled)) {
            // Fidélité doc : ACCEPTED/ENQUEUED/PROCESSING stockés tels quels
            // (ciblable par cancel-enqueued), jamais inventés.
            String local = localStatusFor(polled);
            try {
                if (local != null && isTransitionAllowed(r.getStatus(), local)) {
                    refundDAO.transitionForward(r.getId(), local, dataProviderTxOf(remote));
                } else {
                    refundDAO.touchReconciled(r.getId(), dataProviderTxOf(remote));
                }
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                    "Impossible de traiter votre demande.", e);
            }
            return;
        }
        String reason = null;
        if ("FAILED".equals(polled)) {
            Object d = remote.get("data");
            if (d instanceof Map) reason = PawaPayPaymentService.failureOf((Map<String, Object>) d);
        }
        boolean moved;
        try {
            moved = refundDAO.transitionToFinal(r.getId(), polled, reason, dataProviderTxOf(remote));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (moved && "COMPLETED".equals(polled)) {
            LOG.info(() -> "PawaPay Refund completed (poll): refundId=" + r.getRefundId());
            onRefundCompleted(r);
        }
    }

    static boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    /** Statuts locaux non finaux (le refund peut encore bouger). */
    static boolean isOpenStatus(String status) {
        return "PENDING".equals(status) || "PROCESSING".equals(status)
            || "ACCEPTED".equals(status) || "ENQUEUED".equals(status);
    }

    /**
     * Statut distant PawaPay → statut local BAZAAR (noms doc, jamais
     * inventés). IN_RECONCILIATION → PROCESSING (pas d'équivalent local).
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
     * jamais en arrière, jamais depuis COMPLETED/FAILED, jamais vers
     * un final (les finales passent par transitionToFinal).
     */
    static boolean isTransitionAllowed(String fromLocal, String toLocal) {
        if (fromLocal == null || toLocal == null) return false;
        if (!isOpenStatus(fromLocal) || !isOpenStatus(toLocal)) return false;
        if (fromLocal.equals(toLocal)) return true;
        if ("PENDING".equals(fromLocal)) return true;
        if ("ACCEPTED".equals(fromLocal)) {
            return "ENQUEUED".equals(toLocal) || "PROCESSING".equals(toLocal);
        }
        if ("ENQUEUED".equals(fromLocal)) {
            return "ENQUEUED".equals(toLocal) || "PROCESSING".equals(toLocal);
        }
        return false;
    }

    /** refundId PawaPay : UUIDv4 unique, généré serveur, jamais frontend. */
    static String newRefundId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Garde montant remboursable : 0 < requested ≤ deposit − déjà-remboursé
     * (COMPLETED). Full-only aujourd'hui ; le cumul protège tout partiel futur.
     */
    void assertRefundable(Payment deposit, int requestedAmount) throws AppException, SQLException {
        int already;
        try {
            already = refundDAO.sumCompletedByDepositId(deposit.getProviderTransactionId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        String reason = checkRefundable(requestedAmount, deposit.getAmount(), already);
        if (reason != null) {
            throw new AppException(ErrorCodes.REFUND_NOT_ALLOWED, 409, reason);
        }
    }

    /** Prédicat pur de la garde remboursable (testé). Null = OK. */
    static String checkRefundable(int requestedAmount, int depositAmount, int alreadyRefunded) {
        if (requestedAmount <= 0) return "Montant de remboursement invalide";
        if ((long) requestedAmount + alreadyRefunded > depositAmount) {
            return "Montant supérieur au remboursable (" + (depositAmount - alreadyRefunded) + " FCFA)";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String dataStatusOf(Map<String, Object> remote) {
        if (remote == null) return null;
        Object d = remote.get("data");
        if (!(d instanceof Map)) return null;
        return str(((Map<String, Object>) d).get("status"));
    }

    /** Référence assignée par le provider (reçu destinataire, callback). */
    @SuppressWarnings("unchecked")
    static String dataProviderTxOf(Map<String, Object> remote) {
        if (remote == null) return null;
        Object d = remote.get("data");
        if (!(d instanceof Map)) return null;
        Object tx = ((Map<String, Object>) d).get("providerTransactionId");
        return tx == null ? null : String.valueOf(tx);
    }

    /**
     * Journal idempotent du callback (payment_events réutilisée,
     * clé refund:{id}:{statut}). Retourne true si nouvel événement.
     */
    private boolean journalCallback(Refund r, String eventKey, String rawJson) {
        try {
            return new com.ecommerce.dao.PaymentEventDAO()
                .insertIfNew(r.getPaymentId(), eventKey, rawJson);
        } catch (SQLException e) {
            return true;
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

    private Payment completedDeposit(int orderId) throws AppException {
        try {
            return paymentDAO.findCompletedPawaPayByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    private Refund reload(int id) {
        try {
            return refundDAO.findById(id);
        } catch (SQLException e) {
            return null;
        }
    }

    private Refund findLatestQuiet(int orderId) {
        try {
            return refundDAO.findLatestByOrderId(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    private Refund findOpenQuiet(int orderId) {
        try {
            return refundDAO.findOpenByOrderId(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    /** Vue JSON sans donnée sensible. */
    public static Map<String, Object> maskedView(Refund r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) return m;
        m.put("id", r.getId());
        m.put("orderId", r.getOrderId());
        m.put("refundId", r.getRefundId());
        m.put("depositId", r.getDepositId());
        m.put("providerTransactionId", r.getProviderTransactionId());
        m.put("amount", r.getAmount());
        m.put("currency", r.getCurrency());
        m.put("status", r.getStatus());
        m.put("failureReason", r.getFailureReason());
        m.put("lastReconciledAt", r.getLastReconciledAt());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
