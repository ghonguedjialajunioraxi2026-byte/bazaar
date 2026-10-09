package com.ecommerce.service;

import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.ShipmentDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.Shipment;
import com.ecommerce.model.ShipmentEvent;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.ShipmentStatus;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Livraison interne Phase 12. 1 commande (mono-fournisseur) = 1 expédition.
 * Aucun transporteur externe : carrier INTERNAL, pas de webhooks, pas de GPS.
 */
public class ShipmentService {

    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final NotificationService notificationService = new NotificationService();
    private final OrderService orderService = new OrderService();

    public static final Map<String, String> LABELS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ShipmentStatus.PREPARING, "Préparation");
        m.put(ShipmentStatus.READY_FOR_PICKUP, "Prête à expédier");
        m.put(ShipmentStatus.PICKED_UP, "Prise en charge");
        m.put(ShipmentStatus.IN_TRANSIT, "En transit");
        m.put(ShipmentStatus.OUT_FOR_DELIVERY, "En livraison");
        m.put(ShipmentStatus.DELIVERED, "Livrée");
        m.put(ShipmentStatus.FAILED, "Échec de livraison");
        m.put(ShipmentStatus.RETURNED, "Retournée");
        m.put(ShipmentStatus.CANCELLED, "Annulée");
        LABELS = java.util.Collections.unmodifiableMap(m);
    }

    private Order ownedOrder(int orderId, Integer supplierId) throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Commande introuvable");
        }
        if (supplierId != null && o.getSupplierId() != supplierId) {
            throw new AppException(ErrorCodes.ACCESS_DENIED, 403, "Commande inaccessible");
        }
        return o;
    }

    /** Crée l'expédition (fournisseur propriétaire ou admin). */
    public Shipment createShipment(int orderId, Integer supplierId, String by) throws AppException, SQLException {
        Order o = ownedOrder(orderId, supplierId);
        if ("ANNULEE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande annulée");
        }
        if (!"CONFIRMEE".equals(o.getStatus()) && !"EN_PREPARATION".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande non expédiable à ce stade");
        }
        Shipment existing;
        try {
            existing = shipmentDAO.findByOrder(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (existing != null) return existing; // idempotent
        int id;
        try {
            id = shipmentDAO.create(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (id <= 0) throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.");
        try {
            shipmentDAO.addEvent(id, ShipmentStatus.PREPARING, "Préparation", "Colis en préparation chez le vendeur.", by);
        } catch (SQLException ignored) {}
        // Synchronise la commande : CONFIRMEE → EN_PREPARATION (transition validée).
        if ("CONFIRMEE".equals(o.getStatus())) {
            orderService.updateStatus(orderId, "EN_PREPARATION");
        }
        try {
            notificationService.notifySupplier(o.getSupplierId() > 0 ? o.getSupplierId() : 0,
                "DELIVERY_PREPARING", "Commande en préparation",
                "Commande " + o.getOrderNumber() + " en préparation.",
                "SHIPMENT", id, null,
                "SHIPMENT:" + id + ":PREPARING");
        } catch (Exception ignored) {}
        Shipment s;
        try {
            s = shipmentDAO.findByOrder(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return s;
    }

    /** Avance le statut (transitions validées, atomiques). */
    public Shipment advance(int shipmentId, String toStatus, String reason, String by, Integer supplierId)
            throws AppException, SQLException {
        if (!ShipmentStatus.isKnown(toStatus)) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400, "Statut inconnu");
        }
        if (ShipmentStatus.DELIVERED.equals(toStatus)) {
            // Workflow v2 : plus de passage direct à LIVRÉ (ni fournisseur ni
            // admin) — seule la validation après confirmation client
            // (DeliveryFlowService.validateDelivery) peut solder une livraison.
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Validez la livraison après confirmation client (nouveau workflow)");
        }
        Shipment s = getScoped(shipmentId, supplierId);
        if (!ShipmentStatus.canTransition(s.getStatus(), toStatus)) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400,
                "Transition refusée : " + s.getStatus() + " → " + toStatus);
        }
        boolean ok;
        try {
            ok = shipmentDAO.transition(s.getId(), s.getStatus(), toStatus, reason);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (!ok) {
            throw new AppException(ErrorCodes.CONFLICT, 409, "Expédition déjà modifiée");
        }
        try {
            shipmentDAO.addEvent(s.getId(), toStatus, LABELS.getOrDefault(toStatus, toStatus), reason, by);
        } catch (SQLException ignored) {}
        if (ShipmentStatus.DELIVERED.equals(toStatus)) {
            // Chaîne existante : commande LIVREE → comptabilisation + COD.
            Order o;
            try {
                o = orderDAO.findById(s.getOrderId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (o != null && !"LIVREE".equals(o.getStatus()) && !"ANNULEE".equals(o.getStatus())) {
                if ("EN_PREPARATION".equals(o.getStatus())) {
                    orderService.updateStatus(o.getId(), "EN_LIVRAISON");
                }
                orderService.updateStatus(o.getId(), "LIVREE");
            }
            notifyShipment(o, s.getId(), "DELIVERY_DELIVERED", "Commande livrée",
                "Commande " + (o != null ? o.getOrderNumber() : "") + " livrée.");
        } else if (ShipmentStatus.FAILED.equals(toStatus) || ShipmentStatus.RETURNED.equals(toStatus)) {
            Order o = null;
            try {
                o = orderDAO.findById(s.getOrderId());
            } catch (SQLException ignored) {}
            notifyShipment(o, s.getId(), "DELIVERY_FAILED", "Problème de livraison",
                "Commande " + (o != null ? o.getOrderNumber() : "") + " : "
                    + LABELS.getOrDefault(toStatus, toStatus) + ".");
        } else {
            // Transitions intermédiaires : notification fournisseur réelle (source = shipment).
            String event = null;
            if (ShipmentStatus.PICKED_UP.equals(toStatus)) event = "DELIVERY_SHIPPED";
            else if (ShipmentStatus.IN_TRANSIT.equals(toStatus)) event = "DELIVERY_IN_TRANSIT";
            else if (ShipmentStatus.OUT_FOR_DELIVERY.equals(toStatus)) event = "DELIVERY_OUT_FOR_DELIVERY";
            else if (ShipmentStatus.READY_FOR_PICKUP.equals(toStatus)) event = "DELIVERY_PREPARING";
            if (event != null) {
                Order o = null;
                try {
                    o = orderDAO.findById(s.getOrderId());
                } catch (SQLException ignored) {}
                notifyShipment(o, s.getId(), event, LABELS.getOrDefault(toStatus, toStatus),
                    "Commande " + (o != null ? o.getOrderNumber() : "") + " : "
                        + LABELS.getOrDefault(toStatus, toStatus) + ".");
            }
        }
        try {
            return shipmentDAO.findByOrder(s.getOrderId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /** Notification fournisseur déduite de l'événement livraison (idempotente). */
    private void notifyShipment(Order o, int shipmentId, String eventCode, String title, String message) {
        try {
            if (o != null && o.getSupplierId() > 0) {
                notificationService.notifySupplier(o.getSupplierId(), eventCode, title, message,
                    "SHIPMENT", shipmentId, null,
                    "SHIPMENT:" + shipmentId + ":" + eventCode);
            }
        } catch (Exception ignored) {}
        // Email client (best-effort) pour les étapes clés : remplace WhatsApp.
        try {
            if (o == null || o.getEmail() == null
                    || !EmailService.isValidEmail(o.getEmail())) return;
            String label = null;
            if ("DELIVERY_DELIVERED".equals(eventCode)) label = "livrée";
            else if ("DELIVERY_OUT_FOR_DELIVERY".equals(eventCode)) label = "en cours de livraison";
            else if ("DELIVERY_SHIPPED".equals(eventCode)) label = "expédiée";
            if (label == null) return;
            String tracking = null;
            try {
                Shipment s = shipmentDAO.findByOrder(o.getId());
                if (s != null) tracking = s.getTrackingNumber();
            } catch (Exception ignored) {}
            new EmailService().queueAndSend(o.getEmail(),
                EmailService.shipmentSubject(o.getOrderNumber(), "Commande " + label),
                EmailService.shipmentBody(o.getFullname(), o.getOrderNumber(), label, tracking),
                eventCode, o.getId());
        } catch (Exception ignored) {}
    }

    private Shipment getScoped(int shipmentId, Integer supplierId) throws AppException, SQLException {
        return findShipmentChecked(shipmentId, supplierId);
    }

    private Shipment findShipmentChecked(int shipmentId, Integer supplierId) throws AppException, SQLException {
        // Charge l'expédition puis vérifie le propriétaire via sa commande.
        Shipment s = loadById(shipmentId);
        if (s == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Expédition introuvable");
        }
        Order o;
        try {
            o = orderDAO.findById(s.getOrderId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Commande introuvable");
        }
        if (supplierId != null && o.getSupplierId() != supplierId) {
            throw new AppException(ErrorCodes.ACCESS_DENIED, 403, "Expédition inaccessible");
        }
        return s;
    }

    private Shipment loadById(int shipmentId) throws SQLException {
        // Balayage ciblé via tracking inconnu : on passe par la table directement.
        // (Pas de lecture globale : une seule ligne.)
        try (java.sql.Connection conn = com.ecommerce.config.DBConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT * FROM shipments WHERE id = ?")) {
            ps.setInt(1, shipmentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Shipment s = new Shipment();
                s.setId(rs.getInt("id"));
                s.setOrderId(rs.getInt("order_id"));
                s.setTrackingNumber(rs.getString("tracking_number"));
                s.setCarrier(rs.getString("carrier"));
                s.setStatus(rs.getString("status"));
                s.setFailureReason(rs.getString("failure_reason"));
                s.setShippedAt(rs.getString("shipped_at"));
                s.setDeliveredAt(rs.getString("delivered_at"));
                s.setCreatedAt(rs.getString("created_at"));
                return s;
            }
        }
    }

    public Map<String, Object> detailForGuest(String trackingNumber, String phone) throws AppException, SQLException {
        return detailForGuest(trackingNumber, phone, null);
    }

    /**
     * Détail suivi : preuve téléphone + propriété du compte si une
     * session client est présente (anti-IDOR entre comptes).
     */
    public Map<String, Object> detailForGuest(String trackingNumber, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de suivi requis");
        }
        Shipment s;
        try {
            s = shipmentDAO.findByTracking(trackingNumber.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (s == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Suivi introuvable");
        }
        Order o;
        try {
            o = orderDAO.findById(s.getOrderId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null || phone == null || !phone.trim().equals(o.getPhone() == null ? "" : o.getPhone().trim())) {
            throw new AppException(ErrorCodes.ORDER_ACCESS_DENIED, 403, "Suivi inaccessible");
        }
        CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
        List<ShipmentEvent> events;
        try {
            events = shipmentDAO.events(s.getId(), 200);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shipment", s);
        m.put("events", events);
        m.put("orderNumber", o.getOrderNumber());
        m.put("orderStatus", o.getStatus());
        // Protection acheteur (best-effort) : litige + remboursement visibles
        // par le propriétaire dans son suivi.
        try {
            com.ecommerce.model.Dispute dispute =
                new com.ecommerce.dao.DisputeDAO().findByOrderId(o.getId());
            m.put("dispute", dispute);
        } catch (Exception ignored) {}
        try {
            com.ecommerce.model.Refund refund =
                new com.ecommerce.dao.RefundDAO().findLatestByOrderId(o.getId());
            m.put("refund", refund == null ? null
                : com.ecommerce.pawapay.PawaPayRefundService.maskedView(refund));
        } catch (Exception ignored) {}
        return m;
    }

    public Map<String, Object> detailForOrder(int orderId, Integer supplierId) throws AppException, SQLException {
        Order o = ownedOrder(orderId, supplierId);
        Shipment s;
        try {
            s = shipmentDAO.findByOrder(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shipment", s);
        m.put("events", s == null ? new java.util.ArrayList<>() : eventsOf(s.getId()));
        m.put("orderNumber", o.getOrderNumber());
        m.put("orderStatus", o.getStatus());
        return m;
    }

    private List<ShipmentEvent> eventsOf(int shipmentId) throws AppException {
        try {
            return shipmentDAO.events(shipmentId, 200);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
    }

    /**
     * Résolution expédition → commande pour le bouton « VOIR LA COMMANDE »
     * des notifications fournisseur. Propriété vérifiée (404 hors périmètre,
     * anti-IDOR/anti-énumération). Lecture seule, aucun effet de bord.
     */
    public Map<String, Object> shipmentOrderForSupplier(int shipmentId, int supplierId)
            throws AppException, SQLException {
        Shipment s = findShipmentChecked(shipmentId, supplierId);
        Order o;
        try {
            o = orderDAO.findById(s.getOrderId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Commande introuvable");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("orderNumber", o.getOrderNumber());
        m.put("orderStatus", o.getStatus());
        return m;
    }

    /**
     * Confirmation de réception par le client (chemin historique, conservé
     * pour les expéditions déjà DELIVERED avant le workflow v2).
     * Workflow v2 : utiliser DeliveryFlowService.confirmReceipt (exige
     * DELIVERY_STARTED). Ne déclenche JAMAIS le payout : seul
     * validateDelivery peut lancer le règlement (règle absolue).
     */
    public Shipment confirmReceipt(String trackingNumber, String phone) throws AppException, SQLException {
        return confirmReceipt(trackingNumber, phone, null);
    }

    public Shipment confirmReceipt(String trackingNumber, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        Map<String, Object> d = detailForGuest(trackingNumber, phone, sessionCustomerId);
        Shipment s = (Shipment) d.get("shipment");
        Shipment current = s;
        if (ShipmentStatus.DELIVERED.equals(s.getStatus())) {
            if (!s.isReceiptConfirmed()) {
                markConfirmed(s.getId(), "CLIENT");
            }
        } else {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400, "Réception non confirmable à ce stade");
        }
        try {
            return shipmentDAO.findByOrder(current.getOrderId());
        } catch (SQLException e) {
            return current;
        }
    }

    /** Confirmation admin (chemin historique). Ne déclenche jamais le payout. */
    public Shipment confirmReceiptByAdmin(int orderId) throws AppException, SQLException {
        Shipment s;
        try {
            s = shipmentDAO.findByOrder(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (s == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Expédition introuvable");
        }
        if (!ShipmentStatus.DELIVERED.equals(s.getStatus())) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400, "Réception non confirmable à ce stade");
        }
        if (!s.isReceiptConfirmed()) markConfirmed(s.getId(), "ADMIN");
        try {
            Shipment fresh = shipmentDAO.findByOrder(s.getOrderId());
            return fresh != null ? fresh : s;
        } catch (SQLException e) {
            return s;
        }
    }

    private void markConfirmed(int shipmentId, String by) throws AppException {
        try {
            if (shipmentDAO.markReceiptConfirmed(shipmentId, by)) {
                try {
                    shipmentDAO.addEvent(shipmentId, "RECEIPT_CONFIRMED",
                        "Réception confirmée", "Confirmée par " + by + ".", by);
                } catch (SQLException ignored) {
                    // Événement d'historique best-effort : le flag reste la vérité.
                }
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500,
                "Confirmation non enregistrée, réessayez.", e);
        }
    }

    /** Annulation commande → annulation expédition non terminale. */
    public void cancelForOrder(int orderId) throws SQLException {
        Shipment s = shipmentDAO.findByOrder(orderId);
        if (s == null) return;
        if (ShipmentStatus.canTransition(s.getStatus(), ShipmentStatus.CANCELLED)
                && !ShipmentStatus.DELIVERED.equals(s.getStatus())) {
            if (shipmentDAO.transition(s.getId(), s.getStatus(), ShipmentStatus.CANCELLED, "Commande annulée")) {
                try {
                    shipmentDAO.addEvent(s.getId(), ShipmentStatus.CANCELLED, "Annulée", "Commande annulée.", "SYSTEM");
                } catch (SQLException ignored) {}
            }
        }
    }
}
