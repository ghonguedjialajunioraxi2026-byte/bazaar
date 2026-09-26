package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Offer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Offres de négociation (Phase 8). Historique jamais écrasé :
 * contre-offre = nouvelle ligne + ancienne marquée COUNTERED.
 * Changements d'état via UPDATE conditionnel (WHERE status='PENDING')
 * pour rester cohérents en concurrence.
 */
public class OfferDAO {

    public int create(Offer o) throws SQLException {
        String sql = "INSERT INTO offers (conversation_id, product_id, qty, size, color, catalog_price, unit_price, created_by, status, parent_offer_id) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, o.getConversationId());
            ps.setInt(2, o.getProductId());
            ps.setInt(3, o.getQty());
            ps.setString(4, o.getSize());
            ps.setString(5, o.getColor());
            ps.setInt(6, o.getCatalogPrice());
            ps.setInt(7, o.getUnitPrice());
            ps.setString(8, o.getCreatedBy());
            ps.setString(9, o.getStatus() != null ? o.getStatus() : "PENDING");
            if (o.getParentOfferId() != null) ps.setInt(10, o.getParentOfferId());
            else ps.setNull(10, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    public Offer findById(int id) throws SQLException {
        String sql = "SELECT o.*, p.name AS product_name FROM offers o LEFT JOIN products p ON p.id = o.product_id WHERE o.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Offer> findByConversation(int conversationId) throws SQLException {
        List<Offer> list = new ArrayList<>();
        String sql = "SELECT o.*, p.name AS product_name FROM offers o LEFT JOIN products p ON p.id = o.product_id "
            + "WHERE o.conversation_id = ? ORDER BY o.id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Offer findPendingFromOther(int conversationId, String otherThan) throws SQLException {
        String sql = "SELECT o.*, p.name AS product_name FROM offers o LEFT JOIN products p ON p.id = o.product_id "
            + "WHERE o.conversation_id = ? AND o.status = 'PENDING' AND o.created_by <> ? ORDER BY o.id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.setString(2, otherThan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Transition atomique : ne réussit que si le statut actuel correspond. */
    public boolean transition(int id, String fromStatus, String toStatus) throws SQLException {
        String sql = "UPDATE offers SET status = ? WHERE id = ? AND status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setInt(2, id);
            ps.setString(3, fromStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /** Offres PENDING des conversations d'un fournisseur (vue Négociations). */
    public List<Offer> findPendingBySupplier(int supplierId) throws SQLException {
        List<Offer> list = new ArrayList<>();
        String sql = "SELECT o.*, p.name AS product_name FROM offers o "
            + "JOIN conversations c ON c.id = o.conversation_id "
            + "LEFT JOIN products p ON p.id = o.product_id "
            + "WHERE c.supplier_id = ? AND o.status = 'PENDING' ORDER BY o.id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Supervision admin : offres en attente globales (lecture seule). */
    public List<java.util.Map<String, Object>> findAllPendingForAdmin(int limit, int offset) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT o.id, o.conversation_id, o.product_id, p.name AS product_name, "
            + "s.name AS supplier_name, o.qty, o.unit_price, o.catalog_price, o.created_by, "
            + "o.status, o.created_at FROM offers o "
            + "JOIN conversations c ON c.id = o.conversation_id "
            + "LEFT JOIN suppliers s ON s.id = c.supplier_id "
            + "LEFT JOIN products p ON p.id = o.product_id "
            + "WHERE o.status = 'PENDING' ORDER BY o.id DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("conversationId", rs.getInt("conversation_id"));
                    m.put("productId", rs.getInt("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("qty", rs.getInt("qty"));
                    m.put("unitPrice", rs.getInt("unit_price"));
                    m.put("catalogPrice", rs.getInt("catalog_price"));
                    m.put("createdBy", rs.getString("created_by"));
                    m.put("status", rs.getString("status"));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countPending() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM offers WHERE status='PENDING'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int countToday(int conversationId) throws SQLException {        String sql = "SELECT COUNT(*) FROM offers WHERE conversation_id = ? AND created_at >= NOW() - INTERVAL 1 DAY";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private Offer mapRow(ResultSet rs) throws SQLException {
        Offer o = new Offer();
        o.setId(rs.getInt("id"));
        o.setConversationId(rs.getInt("conversation_id"));
        o.setProductId(rs.getInt("product_id"));
        o.setQty(rs.getInt("qty"));
        o.setSize(rs.getString("size"));
        o.setColor(rs.getString("color"));
        o.setCatalogPrice(rs.getInt("catalog_price"));
        o.setUnitPrice(rs.getInt("unit_price"));
        o.setCreatedBy(rs.getString("created_by"));
        o.setStatus(rs.getString("status"));
        int parent = rs.getInt("parent_offer_id");
        o.setParentOfferId(rs.wasNull() ? null : parent);
        o.setCreatedAt(rs.getString("created_at"));
        try { o.setProductName(rs.getString("product_name")); } catch (SQLException ignored) {}
        return o;
    }
}
