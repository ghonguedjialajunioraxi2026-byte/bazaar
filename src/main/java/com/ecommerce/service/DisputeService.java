package com.ecommerce.service;

import com.ecommerce.dao.DisputeDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PayoutDAO;
import com.ecommerce.model.Dispute;
import com.ecommerce.model.Order;
import com.ecommerce.model.SupplierPayment;
import com.ecommerce.pawapay.PawaPayRefundService;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Protection acheteur : signalement "commande non reçue" → litige.
 * - Une déclaration ne rembourse JAMAIS automatiquement : elle crée
 *   (ou ré-ouvre) un litige et BLOQUE le payout.
 * - Seule une décision admin tracée (REFUND / REJECT) clôt le litige.
 */
public class DisputeService {

    private static final Logger LOG = Logger.getLogger(DisputeService.class.getName());

    private final OrderDAO orderDAO = new OrderDAO();
    private final DisputeDAO disputeDAO = new DisputeDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();

    /**
     * Signale une commande non reçue (propriété vérifiée par
     * numéro + téléphone). Idempotent : un litige OPEN existant est
     * retourné tel quel ; un litige résolu est ré-ouvert.
     */
    public Dispute reportNotReceived(String orderNumber, String phone) throws AppException, SQLException {
        return reportNotReceived(orderNumber, phone, null);
    }

    public Dispute reportNotReceived(String orderNumber, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        if (orderNumber == null || orderNumber.trim().isEmpty()
                || phone == null || phone.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Numéro de commande et téléphone requis");
        }
        Order o = findOwnedOrder(orderNumber.trim(), phone.trim(), sessionCustomerId);
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        if ("ANNULEE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400, "Commande annulée");
        }
        Dispute existing;
        try {
            existing = disputeDAO.findByOrderId(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) {
            if (Dispute.STATUS_OPEN.equals(existing.getStatus())) {
                return existing;
            }
            try {
                disputeDAO.reopen(o.getId(), phone.trim());
                blockPayoutIfOpen(o.getId());
                return disputeDAO.findByOrderId(o.getId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        final int disputeId;
        try {
            disputeId = disputeDAO.create(o.getId(), phone.trim(), Dispute.REASON_NOT_RECEIVED);
        } catch (SQLException e) {
            // Course : litige créé entre-temps → le retourner.
            Dispute raced = findQuiet(o.getId());
            if (raced != null) return raced;
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        blockPayoutIfOpen(o.getId());
        LOG.info(() -> "Dispute opened: order=" + o.getOrderNumber());
        notifySupplier(o, "Litige ouvert",
            "Le client déclare ne pas avoir reçu la commande " + o.getOrderNumber()
                + ". Versement bloqué le temps de l'instruction.");
        try {
            return disputeDAO.findById(disputeId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /** Résolution par tracking (MBOA-TRK-…) ou numéro de commande, propriété vérifiée. */
    private Order findOwnedOrder(String ref, String phone) throws AppException {
        return findOwnedOrder(ref, phone, null);
    }

    private Order findOwnedOrder(String ref, String phone, Integer sessionCustomerId) throws AppException {
        try {
            Order o = orderDAO.findByNumberAndPhone(ref, phone);
            if (o != null) {
                CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
                return o;
            }
            com.ecommerce.model.Shipment s =
                new com.ecommerce.dao.ShipmentDAO().findByTracking(ref);
            if (s != null) {
                Order byShipment = orderDAO.findById(s.getOrderId());
                if (byShipment != null && phone.equals(
                        byShipment.getPhone() == null ? "" : byShipment.getPhone().trim())) {
                    CustomerIsolation.requireOrderVisible(byShipment, sessionCustomerId);
                    return byShipment;
                }
            }
            return null;
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /** Un litige ouvert annule tout payout en attente (protection fonds). */
    private void blockPayoutIfOpen(int orderId) {
        try {
            SupplierPayment sp = payoutDAO.findByOrderId(orderId);
            if (sp != null) payoutDAO.cancelIfOpen(sp.getId(), "Litige ouvert");
        } catch (SQLException ignored) {}
        // Si le payout est déjà ENQUEUED chez PawaPay : tentative d'annulation
        // distante best-effort (jamais bloquante, jamais de faux cancel : un
        // payout déjà exécuté reste marqué tel quel, cf. PAYOUT_ALREADY_SENT).
        try {
            new com.ecommerce.pawapay.PawaPayPayoutService().tryCancelEnqueuedForDispute(orderId);
        } catch (Exception ignored) {}
    }

    /**
     * Résolution admin (rôle MONEY). REFUND → clôt le litige et déclenche
     * le remboursement PawaPay ; REJECT → clôt, le payout redevient possible.
     */
    public Map<String, Object> resolve(int orderId, String decision, String note)
            throws AppException, SQLException {
        if (!"REFUND".equals(decision) && !"REJECT".equals(decision)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Décision invalide (REFUND ou REJECT)");
        }
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        Dispute d;
        try {
            d = disputeDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (d == null || !Dispute.STATUS_OPEN.equals(d.getStatus())) {
            throw new AppException(ErrorCodes.CONFLICT, 409, "Aucun litige ouvert sur cette commande");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("decision", decision);
        if ("REJECT".equals(decision)) {
            try {
                disputeDAO.resolve(orderId, Dispute.STATUS_REJECTED,
                    note == null || note.isEmpty() ? "Litige rejeté après instruction." : note);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            LOG.info(() -> "Dispute rejected: orderId=" + orderId);
            notifySupplier(o, "Litige clôturé",
                "Le litige sur la commande " + o.getOrderNumber() + " a été rejeté après instruction.");
            out.put("status", Dispute.STATUS_REJECTED);
            return out;
        }
        // REFUND : déclenche le remboursement PawaPay réel (peut lever :
        // le litige reste OPEN pour ne pas perdre la demande).
        PawaPayRefundService.RefundOutcome outcome;
        try {
            outcome = new PawaPayRefundService().initiateOrRetry(orderId);
        } catch (AppException e) {
            throw e;
        }
        // Le litige passe en RESOLVED_REFUNDED à la CONFIRMATION PawaPay
        // (onRefundCompleted) ; on garde OPEN jusqu'au COMPLETED, mais on
        // trace la décision admin dans la note (visible au dashboard) pour
        // ne jamais afficher "remboursé" avant les fonds réels.
        try (java.sql.Connection conn = com.ecommerce.config.DBConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                    "UPDATE disputes SET resolution_note = ? WHERE order_id = ? AND status = 'OPEN'")) {
            String trace = "Décision admin : REMBOURSEMENT"
                + (note == null || note.isEmpty() ? "" : " — " + note)
                + " (refund " + outcome.refund.getRefundId()
                + ", statut " + outcome.refund.getStatus() + ").";
            ps.setString(1, trace.substring(0, Math.min(500, trace.length())));
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning(() -> "Dispute resolution note NOT saved for orderId=" + orderId + ": " + e.getMessage());
        }
        LOG.info(() -> "Dispute refund decided: orderId=" + orderId);
        out.put("status", Dispute.STATUS_OPEN);
        out.put("refundId", outcome.refund.getRefundId());
        out.put("refundStatus", outcome.refund.getStatus());
        out.put("duplicate", outcome.duplicate);
        return out;
    }

    public Dispute getByOrder(int orderId) throws SQLException {
        return disputeDAO.findByOrderId(orderId);
    }

    public List<Dispute> list(String status, int limit, int offset) throws SQLException {
        return disputeDAO.findPaged(status, limit, offset);
    }

    private Dispute findQuiet(int orderId) {
        try {
            return disputeDAO.findByOrderId(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    private void notifySupplier(Order o, String title, String message) {
        try {
            if (o.getSupplierId() > 0) {
                new NotificationService().notifySupplier(o.getSupplierId(), "DISPUTE_OPENED", title,
                    message, "DISPUTE", o.getId(), null, "ORDER:" + o.getId() + ":DISPUTE");
            }
        } catch (Exception ignored) {}
    }
}
