package com.ecommerce.pawapay;

import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Façade Deposit PawaPay (Sandbox) : point d'entrée du parcours
 * Checkout → Backend BAZAAR → PawaPay Deposit → MTN/Orange.
 *
 * Responsabilités propres (sans doublon avec {@link PawaPayPaymentService}
 * qui porte l'orchestration HTTP/polling/webhook) :
 * - résolution sécurisée de la commande (orderId OU orderNumber, avec
 *   vérification du téléphone dans les deux cas — anti client A / commande B) ;
 * - garde "commande déjà payée" (aucun nouveau Deposit si soldée) ;
 * - montant effectif = total commande serveur (tout montant client ignoré).
 */
public class PawaPayDepositService {

    private static final Logger LOG = Logger.getLogger(PawaPayDepositService.class.getName());

    private final PawaPayPaymentService deposits = new PawaPayPaymentService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    /**
     * Crée un Deposit. Le montant est TOUJOURS celui de la commande
     * (le paramètre montant du frontend, s'il existe, est ignoré).
     */
    public PawaPayPaymentService.DepositOutcome createDeposit(Integer orderId, String orderNumber, String phone,
            String method, String msisdn) throws AppException, SQLException {
        return createDeposit(orderId, orderNumber, phone, method, msisdn, null);
    }

    public PawaPayPaymentService.DepositOutcome createDeposit(Integer orderId, String orderNumber, String phone,
            String method, String msisdn, Integer sessionCustomerId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Order order = resolveOrder(orderId, orderNumber, phone, sessionCustomerId);
        assertNotAlreadyPaid(order);
        // Le service sous-jacent recalcule tout depuis la commande ;
        // aucun montant client ne lui est transmis (règle anti-fraude).
        PawaPayPaymentService.DepositOutcome outcome = deposits.createDepositOutcome(
            order.getOrderNumber(), order.getPhone(), method, msisdn);
        LOG.info(() -> "PawaPay Deposit completed request: internalRef="
            + outcome.payment.getInternalReference() + " status=" + outcome.payment.getStatus());
        return outcome;
    }

    /** Statut d'un Deposit (polling serveur réconcilié). */
    public Map<String, Object> getDepositStatus(Integer orderId, String orderNumber,
            String phone, String depositId) throws AppException, SQLException {
        return getDepositStatus(orderId, orderNumber, phone, depositId, null);
    }

    public Map<String, Object> getDepositStatus(Integer orderId, String orderNumber,
            String phone, String depositId, Integer sessionCustomerId) throws AppException, SQLException {
        if (depositId != null && !depositId.trim().isEmpty()) {
            return deposits.getDepositStatus(depositId.trim());
        }
        Order order = resolveOrder(orderId, orderNumber, phone, sessionCustomerId);
        return deposits.getOrderPaymentStatus(order.getOrderNumber(), order.getPhone());
    }

    /** Callback PawaPay (idempotent, voir PawaPayPaymentService). */
    public Map<String, Object> handleCallback(Map<String, Object> payload, String rawJson)
            throws AppException, SQLException {
        return deposits.handleWebhook(payload, rawJson);
    }

    /**
     * Redemande à PawaPay le callback final d'un dépôt (perte de callback,
     * incident réseau). Réservé aux dépôts existants de la commande du
     * demandeur (propriété vérifiée) ; ne crée AUCUNE transaction.
     * Endpoint PawaPay exact (doc v2) : POST /v2/deposits/resend-callback/{id}.
     */
    public Map<String, Object> resendCallback(Integer orderId, String orderNumber, String phone)
            throws AppException, SQLException {
        return resendCallback(orderId, orderNumber, phone, null);
    }

    public Map<String, Object> resendCallback(Integer orderId, String orderNumber, String phone,
            Integer sessionCustomerId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        Order order = resolveOrder(orderId, orderNumber, phone, sessionCustomerId);
        Payment p;
        try {
            p = paymentDAO.findLatestPawaPayByOrderId(order.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null || p.getProviderTransactionId() == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun dépôt PawaPay à réconcilier");
        }
        Map<String, Object> resp;
        try {
            resp = new PawaPayClient().resendCallback(p.getProviderTransactionId());
        } catch (AppException e) {
            LOG.warning(() -> "PawaPay resend-callback failed: internalRef=" + p.getInternalReference());
            throw e;
        }
        LOG.info(() -> "PawaPay resend-callback requested: internalRef=" + p.getInternalReference());
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("depositId", p.getProviderTransactionId());
        out.put("status", resp.get("status"));
        Object fr = resp.get("failureReason");
        if (fr != null) out.put("failureReason", fr);
        return out;
    }

    // ------------------------------------------------------------------
    // Résolution sécurisée de la commande
    // ------------------------------------------------------------------

    Order resolveOrder(Integer orderId, String orderNumber, String phone)
            throws AppException, SQLException {
        return resolveOrder(orderId, orderNumber, phone, null);
    }

    /**
     * Résolution avec isolation de compte : si une session client est
     * présente, la commande doit appartenir au compte (ou être invitée
     * historique au même téléphone, déjà prouvé).
     */
    Order resolveOrder(Integer orderId, String orderNumber, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        if (orderNumber != null && !orderNumber.trim().isEmpty()) {
            Order o;
            try {
                o = orderDAO.findByNumberAndPhone(orderNumber.trim(), phone == null ? "" : phone.trim());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (o == null) {
                throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
            }
            com.ecommerce.service.CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
            return o;
        }
        if (orderId != null && orderId > 0) {
            Order o;
            try {
                o = orderDAO.findById(orderId);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (o == null) {
                throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
            }
            // Même exigence que le flux manuel : le téléphone prouve l'appartenance.
            if (phone == null || !phone.trim().equals(o.getPhone())) {
                throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
            }
            com.ecommerce.service.CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
            return o;
        }
        throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande requise (orderId ou orderNumber + phone)");
    }

    /** Refuse un nouveau Deposit si la commande est déjà soldée. */
    void assertNotAlreadyPaid(Order order) throws AppException, SQLException {
        Payment latest;
        Payment latestPawaPay;
        try {
            latest = paymentDAO.findByOrderId(order.getId());
            latestPawaPay = paymentDAO.findLatestPawaPayByOrderId(order.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (latest != null && isPaidStatus(latest.getStatus())) {
            throw new AppException(ErrorCodes.ORDER_ALREADY_PAID, 409, "Commande déjà payée");
        }
        if (latestPawaPay != null && isPaidStatus(latestPawaPay.getStatus())) {
            throw new AppException(ErrorCodes.ORDER_ALREADY_PAID, 409, "Commande déjà payée");
        }
    }

    /** Statuts valant "payé" (PawaPay vérifié ou manuel vérifié). */
    static boolean isPaidStatus(String status) {
        return "COMPLETED".equals(status) || "REUSSIE".equals(status);
    }

    /**
     * Montant effectif du Deposit : TOUJOURS le total serveur.
     * Tout montant envoyé par le navigateur est ignoré (anti-falsification).
     */
    static int effectiveAmount(int orderTotal, Object clientAmount) {
        return orderTotal;
    }

    /** Clé d'idempotence d'un callback : depositId + statut. */
    static String depositEventKey(String depositId, String status) {
        return depositId + ":" + status;
    }
}
