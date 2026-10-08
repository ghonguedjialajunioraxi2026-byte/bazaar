package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Shipment;
import com.ecommerce.model.ShipmentEvent;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expéditions internes (Phase 12). Historique immuable (INSERT seul).
 */
public class ShipmentDAO {

    public static String newTrackingNumber() {
        return "MBOA-TRK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public Shipment findByOrder(int orderId) throws SQLException {
        String sql = "SELECT * FROM shipments WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Shipment findByTracking(String trackingNumber) throws SQLException {
        String sql = "SELECT * FROM shipments WHERE tracking_number = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public int create(int orderId) throws SQLException {
        String sql = "INSERT INTO shipments (order_id, tracking_number, carrier, status) VALUES (?, ?, 'INTERNAL', 'PREPARING')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setString(2, newTrackingNumber());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    /** Transition atomique : réussit seulement depuis le statut attendu. */
    public boolean transition(int id, String fromStatus, String toStatus, String failureReason) throws SQLException {
        String sql = "UPDATE shipments SET status = ?, failure_reason = ?, "
            + "shipped_at = IF(? IN ('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY') AND shipped_at IS NULL, NOW(), shipped_at), "
            + "delivered_at = IF(? = 'DELIVERED', NOW(), delivered_at) "
            + "WHERE id = ? AND status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, failureReason);
            ps.setString(3, toStatus);
            ps.setString(4, toStatus);
            ps.setInt(5, id);
            ps.setString(6, fromStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Marque la réception comme confirmée (acte client/admin daté).
     * Idempotent : une seconde confirmation ne change rien.
     */
    public boolean markReceiptConfirmed(int id, String confirmedBy) throws SQLException {
        String sql = "UPDATE shipments SET receipt_confirmed = 1,"
            + " receipt_confirmed_at = IF(receipt_confirmed_at IS NULL, NOW(), receipt_confirmed_at),"
            + " receipt_confirmed_by = ? WHERE id = ? AND receipt_confirmed = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, confirmedBy);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Workflow v2 : le fournisseur se déclare en route.
     * Atomique + idempotent : un double clic ne crée qu'un seul départ
     * (retourne false si déjà démarré, sans erreur).
     */
    public boolean markDeliveryStarted(int id, String startedBy) throws SQLException {
        String sql = "UPDATE shipments SET delivery_started_at = IF(delivery_started_at IS NULL, NOW(), delivery_started_at),"
            + " delivery_started_by = ? WHERE id = ? AND delivery_started_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startedBy);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Workflow v2 : le fournisseur valide après confirmation client.
     * Atomique + idempotent (un seul payout déclenchable en aval).
     */
    public boolean markDeliveryValidated(int id, String validatedBy) throws SQLException {
        String sql = "UPDATE shipments SET delivery_validated_at = IF(delivery_validated_at IS NULL, NOW(), delivery_validated_at),"
            + " delivery_validated_by = ? WHERE id = ? AND delivery_validated_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, validatedBy);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public void addEvent(int shipmentId, String status, String label, String detail, String source) throws SQLException {        String sql = "INSERT INTO shipment_events (shipment_id, status, label, detail, source) VALUES (?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shipmentId);
            ps.setString(2, status);
            ps.setString(3, label != null ? label.substring(0, Math.min(255, label.length())) : status);
            ps.setString(4, detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
            ps.setString(5, source != null ? source : "SYSTEM");
            ps.executeUpdate();
        }
    }

    public List<ShipmentEvent> events(int shipmentId, int limit) throws SQLException {
        List<ShipmentEvent> list = new ArrayList<>();
        String sql = "SELECT * FROM shipment_events WHERE shipment_id = ? ORDER BY id ASC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shipmentId);
            ps.setInt(2, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShipmentEvent e = new ShipmentEvent();
                    e.setId(rs.getInt("id"));
                    e.setShipmentId(rs.getInt("shipment_id"));
                    e.setStatus(rs.getString("status"));
                    e.setLabel(rs.getString("label"));
                    e.setDetail(rs.getString("detail"));
                    e.setSource(rs.getString("source"));
                    e.setCreatedAt(rs.getString("created_at"));
                    list.add(e);
                }
            }
        }
        return list;
    }

    public List<Shipment> findActive(int limit) throws SQLException {
        List<Shipment> list = new ArrayList<>();
        String sql = "SELECT * FROM shipments WHERE status NOT IN ('DELIVERED','CANCELLED','RETURNED') ORDER BY updated_at DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Supervision admin : expéditions + commande/client (1 requête).
     * @param filter "active" (en cours), "incidents" (FAILED/RETURNED), "all".
     */
    public List<java.util.Map<String, Object>> findForAdmin(String filter, int limit) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT s.*, o.order_number, o.fullname, o.phone, o.city FROM shipments s "
            + "LEFT JOIN orders o ON o.id = s.order_id");
        if ("incidents".equals(filter)) {
            sql.append(" WHERE s.status IN ('FAILED','RETURNED')");
        } else if (!"all".equals(filter)) {
            sql.append(" WHERE s.status NOT IN ('DELIVERED','CANCELLED','RETURNED')");
        }
        sql.append(" ORDER BY s.updated_at DESC LIMIT ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("orderId", rs.getInt("order_id"));
                    m.put("orderNumber", rs.getString("order_number"));
                    m.put("customer", rs.getString("fullname"));
                    m.put("phone", rs.getString("phone"));
                    m.put("city", rs.getString("city"));
                    m.put("trackingNumber", rs.getString("tracking_number"));
                    m.put("carrier", rs.getString("carrier"));
                    m.put("status", rs.getString("status"));
                    m.put("failureReason", rs.getString("failure_reason"));
                    m.put("shippedAt", rs.getString("shipped_at"));
                    m.put("deliveredAt", rs.getString("delivered_at"));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countByStatusGroup(String group) throws SQLException {
        String sql;
        if ("incidents".equals(group)) {
            sql = "SELECT COUNT(*) FROM shipments WHERE status IN ('FAILED','RETURNED')";
        } else if ("all".equals(group)) {
            sql = "SELECT COUNT(*) FROM shipments";
        } else {
            sql = "SELECT COUNT(*) FROM shipments WHERE status NOT IN ('DELIVERED','CANCELLED','RETURNED')";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private Shipment mapRow(ResultSet rs) throws SQLException {
        Shipment s = new Shipment();
        s.setId(rs.getInt("id"));
        s.setOrderId(rs.getInt("order_id"));
        s.setTrackingNumber(rs.getString("tracking_number"));
        s.setCarrier(rs.getString("carrier"));
        s.setStatus(rs.getString("status"));
        s.setFailureReason(rs.getString("failure_reason"));
        s.setShippedAt(rs.getString("shipped_at"));
        s.setDeliveredAt(rs.getString("delivered_at"));
        try { s.setReceiptConfirmed(rs.getBoolean("receipt_confirmed")); } catch (SQLException ignored) {}
        try { s.setReceiptConfirmedAt(rs.getString("receipt_confirmed_at")); } catch (SQLException ignored) {}
        try { s.setReceiptConfirmedBy(rs.getString("receipt_confirmed_by")); } catch (SQLException ignored) {}
        try { s.setDeliveryStartedAt(rs.getString("delivery_started_at")); } catch (SQLException ignored) {}
        try { s.setDeliveryStartedBy(rs.getString("delivery_started_by")); } catch (SQLException ignored) {}
        try { s.setDeliveryValidatedAt(rs.getString("delivery_validated_at")); } catch (SQLException ignored) {}
        try { s.setDeliveryValidatedBy(rs.getString("delivery_validated_by")); } catch (SQLException ignored) {}
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    }
}
