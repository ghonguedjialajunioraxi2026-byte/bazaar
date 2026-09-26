package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Payment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public int create(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, amount, currency, transaction_ref, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod() != null ? p.getMethod() : "WHATSAPP");
            ps.setInt(3, p.getAmount());
            ps.setString(4, p.getCurrency() != null ? p.getCurrency() : "FCFA");
            ps.setString(5, p.getTransactionRef());
            ps.setString(6, p.getStatus() != null ? p.getStatus() : "EN_ATTENTE");
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public Payment findById(int id) throws SQLException {
        String sql = "SELECT * FROM payments WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Payment findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Payment> findAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY created_at DESC LIMIT 500";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** Supervision admin : paiements + commande associée (1 requête, pas de N+1). */
    public List<java.util.Map<String, Object>> findPagedWithOrder(String status, int limit, int offset) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.*, o.order_number, o.fullname FROM payments p "
            + "LEFT JOIN orders o ON o.id = p.order_id");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            sql.append(" WHERE p.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, Math.min(100, limit)));
        params.add(Math.max(0, offset));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, String.valueOf(p));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("orderId", rs.getInt("order_id"));
                    m.put("orderNumber", rs.getString("order_number"));
                    m.put("customer", rs.getString("fullname"));
                    m.put("method", rs.getString("method"));
                    m.put("provider", rs.getString("provider"));
                    m.put("amount", rs.getInt("amount"));
                    m.put("currency", rs.getString("currency"));
                    m.put("status", rs.getString("status"));
                    m.put("transactionRef", rs.getString("transaction_ref"));
                    m.put("failureReason", rs.getString("failure_reason"));
                    m.put("paidAt", rs.getString("paid_at"));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM payments"
            + (status != null && !status.isEmpty() ? " WHERE status = ?" : "");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null && !status.isEmpty()) ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void updateStatus(int id, String status, String transactionRef) throws SQLException {
        String sql = "UPDATE payments SET status = ?, transaction_ref = ?, paid_at = IF(?='REUSSIE',NOW(),NULL) WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, transactionRef);
            ps.setString(3, status);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public int totalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'REUSSIE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public List<Payment> findByStatus(String status) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE status = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getInt("id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setMethod(rs.getString("method"));
        p.setAmount(rs.getInt("amount"));
        p.setCurrency(rs.getString("currency"));
        p.setTransactionRef(rs.getString("transaction_ref"));
        p.setStatus(rs.getString("status"));
        try { p.setPaidAt(rs.getString("paid_at")); } catch (SQLException e) { p.setPaidAt(null); }
        p.setCreatedAt(rs.getString("created_at"));
        try { p.setProvider(rs.getString("provider")); } catch (SQLException ignored) {}
        try { p.setIdempotenceKey(rs.getString("idempotence_key")); } catch (SQLException ignored) {}
        try { p.setFailureReason(rs.getString("failure_reason")); } catch (SQLException ignored) {}
        try {
            int vb = rs.getInt("verified_by");
            p.setVerifiedBy(rs.wasNull() ? null : vb);
        } catch (SQLException ignored) {}
        return p;
    }

    public Payment findByIdempotenceKey(String key) throws SQLException {
        String sql = "SELECT * FROM payments WHERE idempotence_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public int createFull(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, provider, amount, currency, transaction_ref, idempotence_key, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod());
            ps.setString(3, p.getProvider());
            ps.setInt(4, p.getAmount());
            ps.setString(5, p.getCurrency() != null ? p.getCurrency() : "FCFA");
            ps.setString(6, p.getTransactionRef());
            ps.setString(7, p.getIdempotenceKey());
            ps.setString(8, p.getStatus() != null ? p.getStatus() : "EN_ATTENTE");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    public void updateReference(int id, String transactionRef) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET transaction_ref = ? WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setString(1, transactionRef);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markVerified(int id, int adminId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'REUSSIE', verified_by = ?, verified_at = NOW(), paid_at = NOW(), failure_reason = NULL WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setInt(1, adminId);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markFailed(int id, String reason) throws SQLException {
        String safe = reason == null ? null : reason.substring(0, Math.min(255, reason.length()));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'ECHOUEE', failure_reason = ? WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setString(1, safe);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markCancelled(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'ANNULEE' WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Rouvre une tentative après échec (nouvelle référence à fournir). */
    public void reopen(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'EN_ATTENTE' WHERE id = ? AND status = 'ECHOUEE'")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
