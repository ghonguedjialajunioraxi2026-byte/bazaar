package com.ecommerce.service;

import com.ecommerce.dao.DisputeDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.PayoutDAO;
import com.ecommerce.dao.RefundDAO;
import com.ecommerce.dao.ShipmentDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Refund;
import com.ecommerce.model.Shipment;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow livraison BAZAAR v2 : 2 actions explicites, pas de statuts multiples.
 *
 * <pre>
 *   DELIVERY_STARTED          : le fournisseur se déclare en route
 *   CLIENT_RECEIPT_CONFIRMED  : le client confirme avoir PHYSIQUEMENT reçu le colis
 *   DELIVERY_VALIDATED        : le fournisseur valide -&gt; déclenche le financier
 * </pre>
 *
 * Règles absolues (vérifiées serveur, jamais de confiance frontend) :
 * - le payout ne part QUE si paiement confirmé + départ + réception +
 *   validation sont tous vrais (garde finale = PawaPayPayoutService,
 *   qui impose en plus LIVREE + receipt_confirmed + dépôt + ni litige ni refund) ;
 * - CONFIRMER la réception ne paie JAMAIS le fournisseur ;
 * - tout est idempotent (double clic, retry, 2 onglets = 1 seul effet).
 *
 * Stockage : colonnes delivery_started_at/validated_at + flag
 * receipt_confirmed existant sur shipments ; historique immuable dans
 * shipment_events (DELIVERY_STARTED, RECEIPT_CONFIRMED, DELIVERY_VALIDATED).
 * Les ENUM orders.status / shipments.status sont CONSERVÉS (paiements,
 * ledger et écrans admin en dépendent) mais pilotés par ce workflow.
 */
public class DeliveryFlowService {

    public static final String EV_STARTED = "DELIVERY_STARTED";
    public static final String EV_RECEIPT = "RECEIPT_CONFIRMED";
    public static final String EV_VALIDATED = "DELIVERY_VALIDATED";

    /**
     * Action fournisseur à afficher pour une commande (workflow v2, sans
     * DÉTAIL). Mêmes branches que le frontend (cartes inline + modale) :
     * <pre>
     *   START       : paiement confirmé + livraison non démarrée -> [ EN COURS DE LIVRAISON ]
     *   WAIT_CLIENT : démarrée + réception non confirmée         -> attente client
     *   LIVRE       : réception confirmée + non validée          -> [ COMMANDE LIVRÉE ]
     *   PAYOUT      : validée + payout non terminé               -> règlement en cours
     *   DONE        : validée + payout terminé                   -> commande terminée
     *   NONE        : annulée ou non éligible                    -> aucune action
     * </pre>
     * Pure (sans DB) : testée par DeliverySupplierActionTest.
     */
    public enum SupplierAction { START, WAIT_CLIENT, LIVRE, PAYOUT, DONE, NONE }

    public static SupplierAction supplierAction(String orderStatus, boolean paymentConfirmed,
            boolean started, boolean receipt, boolean validated, String payoutStatus) {
        if ("ANNULEE".equals(orderStatus)) return SupplierAction.NONE;
        boolean payoutDone = "COMPLETED".equals(payoutStatus) || "PAID".equals(payoutStatus);
        if (validated) return payoutDone ? SupplierAction.DONE : SupplierAction.PAYOUT;
        if (receipt) {
            // La réception sans départ est incohérente : le serveur la refuse,
            // l'interface n'affiche LIVRÉ que si le départ est confirmé.
            return started ? SupplierAction.LIVRE : SupplierAction.NONE;
        }
        if (started) return SupplierAction.WAIT_CLIENT;
        if (paymentConfirmed
                && ("CONFIRMEE".equals(orderStatus) || "EN_PREPARATION".equals(orderStatus)
                    || "EN_LIVRAISON".equals(orderStatus))) {
            return SupplierAction.START;
        }
        return SupplierAction.NONE;
    }

    private final OrderDAO orderDAO = new OrderDAO();
    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final RefundDAO refundDAO = new RefundDAO();
    private final DisputeDAO disputeDAO = new DisputeDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();
    private final NotificationService notificationService = new NotificationService();

    // ------------------------------------------------------------------
    // État du flux pour une commande (fournisseur / client / admin).
    // ------------------------------------------------------------------
    public Map<String, Object> flowState(int orderId) throws AppException, SQLException {
        Order o = orderById(orderId);
        Shipment s = shipmentDAO.findByOrder(orderId);
        return stateView(o, s);
    }

    /** Lecture fournisseur : 404 si la commande n'est pas la sienne (anti-énumération). */
    public Map<String, Object> flowStateVisibleToSupplier(int orderId, int supplierId)
            throws AppException, SQLException {
        Order o = ownedOrder(orderId, supplierId);
        return stateView(o, shipmentDAO.findByOrder(orderId));
    }

    /** Lecture client (preuve téléphone) ou admin (supervision). */
    public Map<String, Object> flowStateVisibleToClient(String orderNumber, String phone, boolean admin)
            throws AppException, SQLException {
        return flowStateVisibleToClient(orderNumber, phone, admin, null);
    }

    public Map<String, Object> flowStateVisibleToClient(String orderNumber, String phone, boolean admin,
            Integer sessionCustomerId) throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findByNumberAndPhone(orderNumber, phone);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(admin ? ErrorCodes.RESOURCE_NOT_FOUND : ErrorCodes.ORDER_ACCESS_DENIED,
                admin ? 404 : 403, admin ? "Commande introuvable" : "Commande inaccessible");
        }
        CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
        return stateView(o, shipmentDAO.findByOrder(o.getId()));
    }

    /** Normalisation téléphone : 9 derniers chiffres (même règle que OrderDAO). */
    static String last9(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("\\D", "");
        return d.length() <= 9 ? d : d.substring(d.length() - 9);
    }

    /** Confirmation client par numéro + téléphone (preuve de propriété, format insensible). */
    public Map<String, Object> confirmReceiptByNumber(String orderNumber, String phone)
            throws AppException, SQLException {
        return confirmReceiptByNumber(orderNumber, phone, null);
    }

    public Map<String, Object> confirmReceiptByNumber(String orderNumber, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findByNumberAndPhone(orderNumber, phone);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_ACCESS_DENIED, 403, "Commande inaccessible");
        }
        CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
        return confirmReceipt(o.getId(), phone);
    }

    /**
     * Confirmation par session client authentifiée (Bearer) : la commande doit
     * appartenir au customerId de la session. Les commandes invitées historiques
     * (customer_id NULL/0) restent sur la preuve téléphone.
     */
    public Map<String, Object> confirmReceiptByCustomer(int customerId, String orderNumber)
            throws AppException, SQLException {
        if (customerId <= 0 || orderNumber == null || orderNumber.isBlank()) {
            throw new AppException(ErrorCodes.AUTH_REQUIRED, 401, "Authentification requise");
        }
        Order o;
        try {
            o = orderDAO.findByNumber(orderNumber.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (o == null || o.getCustomerId() != customerId) {
            throw new AppException(ErrorCodes.ORDER_ACCESS_DENIED, 403, "Commande inaccessible");
        }
        return confirmReceipt(o.getId(), o.getPhone());
    }

    private Map<String, Object> stateView(Order o, Shipment s) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("orderNumber", o.getOrderNumber());
        m.put("orderStatus", o.getStatus());
        boolean started = s != null && s.isDeliveryStarted();
        boolean receipt = s != null && s.isReceiptConfirmed();
        boolean validated = s != null && s.isDeliveryValidated();
        m.put("deliveryStarted", started);
        m.put("deliveryStartedAt", s == null ? null : s.getDeliveryStartedAt());
        m.put("receiptConfirmed", receipt);
        m.put("receiptConfirmedAt", s == null ? null : s.getReceiptConfirmedAt());
        m.put("deliveryValidated", validated);
        m.put("deliveryValidatedAt", s == null ? null : s.getDeliveryValidatedAt());
        m.put("trackingNumber", s == null ? null : s.getTrackingNumber());
        m.put("paymentConfirmed", isPaymentConfirmed(o));
        try {
            com.ecommerce.model.SupplierPayment p = payoutDAO.findByOrderId(o.getId());
            if (p != null) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("status", p.getStatus());
                pm.put("amount", p.getAmount());
                pm.put("reference", p.getReference());
                m.put("payout", pm);
            } else {
                m.put("payout", null);
            }
        } catch (Exception ignored) {
            m.put("payout", null);
        }
        return m;
    }

    // ------------------------------------------------------------------
    // ÉTAPE 1 : le fournisseur se déclare EN COURS DE LIVRAISON.
    // ------------------------------------------------------------------
    public Map<String, Object> startDelivery(int supplierId, int orderId)
            throws AppException, SQLException {
        Order o = ownedOrder(orderId, supplierId);
        requireLivable(o);
        requirePaymentConfirmed(o);
        requireNoBlockingRefund(o.getId());
        requireNoOpenDispute(o.getId());

        Shipment s = shipmentDAO.findByOrder(orderId);
        if (s == null) {
            // Crée le suivi (idempotent) + synchronise CONFIRMEE -> EN_PREPARATION.
            s = shipmentService.createShipment(orderId, supplierId, "SUPPLIER");
        }
        if (s.isDeliveryValidated()) {
            throw new AppException(ErrorCodes.CONFLICT, 409, "Livraison déjà validée");
        }
        boolean first = shipmentDAO.markDeliveryStarted(s.getId(), "SUPPLIER");
        if (first) {
            safeEvent(s.getId(), EV_STARTED, "En cours de livraison",
                "Le fournisseur est en route pour livrer la commande " + o.getOrderNumber() + ".",
                "SUPPLIER");
        }
        // Pilote la commande : EN_PREPARATION -> EN_LIVRAISON (transition validée,
        // effets conservés). Idempotent : déjà EN_LIVRAISON = rien à faire.
        if (!"EN_LIVRAISON".equals(o.getStatus())) {
            orderService.updateStatus(orderId, "EN_LIVRAISON");
        }
        // Notification client : timeline invité dérivée (auto) + email (best-effort).
        sendClientStartedEmail(o, s);
        Map<String, Object> out = stateView(orderById(orderId), shipmentDAO.findByOrder(orderId));
        out.put("duplicate", !first);
        return out;
    }

    // ------------------------------------------------------------------
    // ÉTAPE 2 : le client confirme avoir PHYSIQUEMENT reçu le colis.
    // Ne déclenche JAMAIS le payout (règle absolue).
    // ------------------------------------------------------------------
    public Map<String, Object> confirmReceipt(int orderId, String phone)
            throws AppException, SQLException {
        Order o = orderById(orderId);
        // Comparaison insensible au format (+237, espaces, tirets) : 9 derniers chiffres.
        String want = last9(phone);
        String have = last9(o.getPhone());
        if (want.isEmpty() || have.isEmpty() || !want.equals(have)) {
            throw new AppException(ErrorCodes.ORDER_ACCESS_DENIED, 403, "Commande inaccessible");
        }
        if ("ANNULEE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande annulée");
        }
        if ("LIVREE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.CONFLICT, 409, "Commande déjà finalisée");
        }
        requireNoBlockingRefund(orderId);
        Shipment s = shipmentDAO.findByOrder(orderId);
        if (s == null || !s.isDeliveryStarted()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Le fournisseur n'a pas encore déclaré la livraison");
        }
        if (s.isReceiptConfirmed()) {
            Map<String, Object> out = stateView(o, s);
            out.put("duplicate", true);
            return out; // idempotent : déjà confirmée
        }
        shipmentDAO.markReceiptConfirmed(s.getId(), "CLIENT");
        safeEvent(s.getId(), EV_RECEIPT, "Réception confirmée",
            "Le client confirme avoir physiquement reçu la commande " + o.getOrderNumber() + ".",
            "CLIENT");
        // Notification fournisseur (persistée, visible dans son espace).
        safeNotifySupplier(o.getSupplierId(), "RECEIPT_CONFIRMED",
            "Commande reçue confirmée",
            "Le client a confirmé la réception de la commande " + o.getOrderNumber()
                + ". Vous pouvez maintenant valider la livraison.",
            s.getId(), "RECEIPT:" + s.getId() + ":CONFIRMED");
        Map<String, Object> out = stateView(orderById(orderId), shipmentDAO.findByOrder(orderId));
        out.put("duplicate", false);
        return out;
    }

    // ------------------------------------------------------------------
    // ÉTAPE 3 : le fournisseur valide -> financier (ledger + payout PawaPay).
    // ------------------------------------------------------------------
    public Map<String, Object> validateDelivery(int supplierId, int orderId)
            throws AppException, SQLException {
        Order o = ownedOrder(orderId, supplierId);
        if ("ANNULEE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande annulée");
        }
        requirePaymentConfirmed(o);
        requireNoBlockingRefund(orderId);
        requireNoOpenDispute(orderId);
        Shipment s = shipmentDAO.findByOrder(orderId);
        if (s == null || !s.isDeliveryStarted()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Livraison non démarrée");
        }
        if (!s.isReceiptConfirmed()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Le client n'a pas encore confirmé la réception");
        }
        // Un seul payout par commande : ligne existante finalisée ou ouverte ?
        try {
            com.ecommerce.model.SupplierPayment existing = payoutDAO.findByOrderId(orderId);
            if (existing != null && isFinalPayout(existing.getStatus())) {
                throw new AppException(ErrorCodes.CONFLICT, 409, "Règlement déjà effectué");
            }
        } catch (AppException ae) {
            throw ae;
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }

        boolean first = shipmentDAO.markDeliveryValidated(s.getId(), "SUPPLIER");
        if (first) {
            safeEvent(s.getId(), EV_VALIDATED, "Livraison validée",
                "Le fournisseur a validé la livraison de " + o.getOrderNumber()
                    + " après confirmation client. Règlement en cours.",
                "SUPPLIER");
        }
        // Comptabilisation via le chemin officiel (LIVREE -> settle + COD).
        if (!"LIVREE".equals(o.getStatus())) {
            orderService.updateStatus(orderId, "LIVREE");
        }
        // Aligne le suivi interne (terminal, sans passer par les étapes кноп UI).
        markShipmentDelivered(s.getId());

        // Déclenche le payout PawaPay EXISTANT (idempotent, réconcilié).
        // La garde absolue est re-vérifiée côté PawaPayPayoutService
        // (LIVREE + réception + dépôt + ni litige ni refund).
        String payoutOutcome = "REQUESTED";
        String payoutDetail = null;
        try {
            new com.ecommerce.pawapay.PawaPayPayoutService().initiateOrRetry(orderId);
        } catch (AppException ae) {
            payoutOutcome = "BLOCKED";
            payoutDetail = ae.getMessage();
        } catch (Exception e) {
            payoutOutcome = "BLOCKED";
            payoutDetail = "Règlement à reprendre (réessayable sans risque).";
        }
        safeNotifySupplier(o.getSupplierId(),
            "BLOCKED".equals(payoutOutcome) ? "PAYOUT_REQUESTED" : "DELIVERY_VALIDATED",
            "BLOCKED".equals(payoutOutcome) ? "Règlement à compléter" : "Livraison validée",
            "BLOCKED".equals(payoutOutcome)
                ? ("Livraison " + o.getOrderNumber() + " validée mais règlement bloqué : "
                    + payoutDetail + " Vérifiez le téléphone payout de la boutique.")
                : ("Livraison " + o.getOrderNumber() + " validée. Le règlement est en cours "
                    + "(commission BAZAAR 1 % + frais PawaPay déduits, calcul serveur)."),
            s.getId(), "VALIDATED:" + s.getId() + ":" + payoutOutcome);
        Map<String, Object> out = stateView(orderById(orderId), shipmentDAO.findByOrder(orderId));
        out.put("duplicate", !first);
        out.put("payoutOutcome", payoutOutcome);
        if (payoutDetail != null) out.put("payoutDetail", payoutDetail);
        return out;
    }

    // ------------------------------------------------------------------
    // Gardes partagées.
    // ------------------------------------------------------------------
    private Order orderById(int orderId) throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    /** Propriété fournisseur (404 si absente, 403 si autre boutique : anti-IDOR + anti-énumération). */
    private Order ownedOrder(int orderId, int supplierId) throws AppException, SQLException {
        Order o = orderById(orderId);
        if (o.getSupplierId() != supplierId) {
            // Même réponse que "introuvable" quand la commande n'appartient pas :
            // on ne révèle pas l'existence des commandes d'autrui.
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    private void requireLivable(Order o) throws AppException {
        String st = o.getStatus();
        if ("ANNULEE".equals(st)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande annulée");
        }
        if ("LIVREE".equals(st)) {
            throw new AppException(ErrorCodes.CONFLICT, 409, "Commande déjà livrée");
        }
        if (!"CONFIRMEE".equals(st) && !"EN_PREPARATION".equals(st) && !"EN_LIVRAISON".equals(st)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Commande non éligible à la livraison à ce stade");
        }
    }

    /**
     * Paiement client confirmé ? Même règle que la garde CONFIRMEE :
     * ligne REUSSIE/COMPLETED, ou paiement à la livraison (soldé à la
     * livraison), ou commande historique WHATSAPP sans ligne.
     */
    static boolean isPaymentConfirmed(Order o, Payment pay) {
        if (o == null) return false;
        if ("PAIEMENT_LIVRAISON".equals(o.getPaymentMethod())) return true;
        if (pay == null) return "WHATSAPP".equals(o.getPaymentMethod());
        return "REUSSIE".equals(pay.getStatus()) || "COMPLETED".equals(pay.getStatus());
    }

    private boolean isPaymentConfirmed(Order o) {
        try {
            return isPaymentConfirmed(o, paymentDAO.findByOrderId(o.getId()));
        } catch (Exception e) {
            return false;
        }
    }

    private void requirePaymentConfirmed(Order o) throws AppException {
        Payment pay;
        try {
            pay = paymentDAO.findByOrderId(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (!isPaymentConfirmed(o, pay)) {
            throw new AppException(ErrorCodes.PAYMENT_REQUIRED, 400,
                "Paiement client non confirmé : livraison impossible");
        }
    }

    private void requireNoBlockingRefund(int orderId) throws AppException {
        Refund r;
        try {
            r = refundDAO.findLatestByOrderId(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (r != null && !"FAILED".equals(r.getStatus())) {
            throw new AppException(ErrorCodes.CONFLICT, 409,
                "Remboursement en cours ou effectué : action impossible");
        }
    }

    private void requireNoOpenDispute(int orderId) throws AppException {
        boolean open;
        try {
            open = disputeDAO.hasOpenDispute(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        if (open) {
            throw new AppException(ErrorCodes.DISPUTE_OPEN, 409,
                "Litige ouvert : résolvez la réclamation avant de continuer");
        }
    }

    private static boolean isFinalPayout(String status) {
        return "COMPLETED".equals(status) || "PAID".equals(status);
    }

    /** Aligne shipments.status=DELIVERED à la validation (terminal v2, sans étapes UI). */
    private void markShipmentDelivered(int shipmentId) {
        try (java.sql.Connection conn = com.ecommerce.config.DBConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "UPDATE shipments SET status = 'DELIVERED',"
                 + " delivered_at = IF(delivered_at IS NULL, NOW(), delivered_at)"
                 + " WHERE id = ? AND status NOT IN ('DELIVERED','CANCELLED','RETURNED','FAILED')")) {
            ps.setInt(1, shipmentId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void safeEvent(int shipmentId, String code, String label, String detail, String by) {
        try {
            shipmentDAO.addEvent(shipmentId, code, label, detail, by);
        } catch (Exception ignored) {}
    }

    private void safeNotifySupplier(int supplierId, String code, String title,
                                    String message, int shipmentId, String dedup) {
        try {
            if (supplierId > 0) {
                notificationService.notifySupplier(supplierId, code, title, message,
                    "SHIPMENT", shipmentId, "/fournisseur.html", dedup);
            }
        } catch (Exception ignored) {}
    }

    /** Email client au départ (best-effort, jamais bloquant). */
    private void sendClientStartedEmail(Order o, Shipment s) {
        try {
            if (o.getEmail() == null || !EmailService.isValidEmail(o.getEmail())) return;
            String tracking = s == null ? null : s.getTrackingNumber();
            new EmailService().queueAndSend(o.getEmail(),
                EmailService.shipmentSubject(o.getOrderNumber(), "en cours de livraison"),
                "<p>Bonjour " + escape(o.getFullname()) + ",</p>"
                    + "<p>Votre commande <strong>" + escape(o.getOrderNumber()) + "</strong>"
                    + " est en cours de livraison. Votre fournisseur est en route"
                    + (tracking == null ? "." : " (suivi " + escape(tracking) + ").") + "</p>"
                    + "<p>Lorsque vous avez <strong>physiquement reçu</strong> votre colis,"
                    + " ouvrez le détail de la commande dans « Mes commandes » et appuyez sur"
                    + " <strong>COMMANDE REÇUE</strong>.</p>",
                EV_STARTED, o.getId());
        } catch (Exception ignored) {}
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
