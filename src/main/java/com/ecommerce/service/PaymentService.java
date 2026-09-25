package com.ecommerce.service;

import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.List;

/**
 * Paiements Phase 11. Règles :
 * - Montant/devise recalculés serveur (jamais du navigateur).
 * - Propriété commande vérifiée (numéro + téléphone invité).
 * - Idempotence par clé PAY-&lt;orderId&gt; (pas de double init).
 * - Confirmation = événement serveur (vérification admin ou livraison),
 *   jamais une déclaration du frontend.
 * - Devise imposée : FCFA.
 */
public class PaymentService {

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    public static final String CURRENCY = "FCFA";

    private Order ownedOrder(String orderNumber, String phone) throws AppException, SQLException {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de commande requis");
        }
        Order o;
        try {
            o = orderDAO.findByNumberAndPhone(orderNumber.trim(), phone == null ? "" : phone.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    /**
     * Initialise le paiement d'une commande (idempotent).
     * La méthode doit faire partie des méthodes proposées.
     */
    public Payment initPayment(String orderNumber, String phone, String method) throws AppException, SQLException {
        Order o = ownedOrder(orderNumber, phone);
        if (!PaymentProviders.offeredMethods().contains(method)) {
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 400, "Moyen de paiement indisponible");
        }
        if (!"EN_ATTENTE".equals(o.getStatus()) && !"CONFIRMEE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 400, "Commande non payable à ce stade");
        }
        Payment existing;
        try {
            existing = paymentDAO.findByIdempotenceKey("PAY-" + o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) return existing;
        PaymentProvider provider = PaymentProviders.forMethod(method);
        Payment p = new Payment();
        p.setOrderId(o.getId());
        p.setMethod(method);
        p.setProvider(provider != null ? provider.getId() : "LEGACY");
        p.setAmount(o.getTotal());
        p.setCurrency(CURRENCY);
        p.setStatus("EN_ATTENTE");
        p.setIdempotenceKey("PAY-" + o.getId());
        try {
            int id = paymentDAO.createFull(p);
            p.setId(id);
        } catch (SQLException e) {
            // Course : une autre initialisation a gagné → relire (idempotence).
            try {
                Payment concurrent = paymentDAO.findByIdempotenceKey("PAY-" + o.getId());
                if (concurrent != null) return concurrent;
            } catch (SQLException ignored) {}
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return p;
    }

    public Payment getStatus(String orderNumber, String phone) throws AppException, SQLException {
        Order o = ownedOrder(orderNumber, phone);
        Payment p;
        try {
            p = paymentDAO.findByOrderId(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun paiement initié");
        }
        return p;
    }

    /** Le client transmet sa référence MoMo (vérification admin ultérieure). */
    public Payment submitReference(String orderNumber, String phone, String transactionRef) throws AppException, SQLException {
        Order o = ownedOrder(orderNumber, phone);
        Payment p;
        try {
            p = paymentDAO.findByOrderId(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Initiez d'abord le paiement");
        }
        if (!"EN_ATTENTE".equals(p.getStatus()) && !"ECHOUEE".equals(p.getStatus())) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 409, "Paiement déjà traité");
        }
        PaymentProvider provider = PaymentProviders.forMethod(p.getMethod());
        if (provider == null || !provider.isReferenceValid(transactionRef)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Référence de transaction invalide");
        }
        // Nouvelle tentative explicite après échec (l'historique détaillé des
        // tentatives n'est pas conservé : seule la dernière référence est gardée).
        if ("ECHOUEE".equals(p.getStatus())) {
            try {
                paymentDAO.reopen(p.getId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            p.setStatus("EN_ATTENTE");
        }
        try {
            paymentDAO.updateReference(p.getId(), transactionRef.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        p.setTransactionRef(transactionRef.trim());
        return p;
    }

    /** Vérification admin : seule source de confirmation MoMo. */
    public void verifyPayment(int paymentId, int adminId) throws AppException, SQLException {
        Payment p;
        try {
            p = paymentDAO.findById(paymentId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Paiement introuvable");
        }
        if (!"EN_ATTENTE".equals(p.getStatus())) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 409, "Paiement déjà traité");
        }
        try {
            paymentDAO.markVerified(paymentId, adminId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        notifyParties(p, "Paiement confirmé", "Le paiement de la commande a été confirmé.");
    }

    public void failPayment(int paymentId, int adminId, String reason) throws AppException, SQLException {
        Payment p;
        try {
            p = paymentDAO.findById(paymentId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Paiement introuvable");
        }
        if (!"EN_ATTENTE".equals(p.getStatus())) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 409, "Paiement déjà traité");
        }
        try {
            paymentDAO.markFailed(paymentId, reason);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /** Confirmation COD à la livraison (événement serveur réel). */
    public void confirmOnDelivery(int orderId) throws SQLException {
        Payment p = paymentDAO.findByOrderId(orderId);
        if (p != null && "EN_ATTENTE".equals(p.getStatus()) && "PAIEMENT_LIVRAISON".equals(p.getMethod())) {
            paymentDAO.updateStatus(p.getId(), "REUSSIE", p.getTransactionRef());
        }
    }

    /** Remboursement comptable : marque ANNULEE (le mouvement PSP reste manuel, documenté). */
    public void markRefunded(int paymentId) throws SQLException {
        Payment p = paymentDAO.findById(paymentId);
        if (p != null && "REUSSIE".equals(p.getStatus())) {
            paymentDAO.updateStatus(p.getId(), "ANNULEE", p.getTransactionRef());
        }
    }

    /** Remboursement lié à une commande (retrouvé via sa ligne). */
    public void markRefunded(int orderId, boolean byOrder) throws SQLException {
        if (!byOrder) {
            markRefunded(orderId);
            return;
        }
        Payment p = paymentDAO.findByOrderId(orderId);
        if (p != null && "REUSSIE".equals(p.getStatus())) {
            paymentDAO.updateStatus(p.getId(), "ANNULEE", p.getTransactionRef());
        }
    }

    public List<Payment> allPayments() throws SQLException {
        return paymentDAO.findAll();
    }

    private void notifyParties(Payment p, String title, String message) {
        try {
            Order o = orderDAO.findById(p.getOrderId());
            if (o != null && o.getSupplierId() > 0) {
                notificationDAO.createForSupplier(o.getSupplierId(), title, message + " Commande " + o.getOrderNumber() + ".", "PAYMENT");
            }
        } catch (Exception ignored) {}
    }
}
