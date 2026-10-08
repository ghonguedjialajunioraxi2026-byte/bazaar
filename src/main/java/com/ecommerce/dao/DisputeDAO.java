package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Dispute;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Litiges "commande non reçue" (table disputes, 1 ligne par commande).
 */
public class DisputeDAO {

    public int create(int orderId, String phone, String reason) throws SQLException {
        String sql = "INSERT INTO disputes (order_id, phone, reason, status) VALUES (?, ?, ?, 'OPEN')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setString(2, phone);
            ps.setString(3, reason != null ? reason : Dispute.REASON_NOT_RECEIVED);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public Dispute findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM disputes WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Dispute findById(int id) throws SQLException {
        String sql = "SELECT * FROM disputes WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public boolean hasOpenDispute(int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM disputes WHERE order_id = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /** Ré-ouvre un litige résolu (nouvelle déclaration client). */
    public void reopen(int orderId, String phone) throws SQLException {
        String sql = "UPDATE disputes SET status = 'OPEN', phone = ?, resolution_note = NULL,"
            + " resolved_at = NULL WHERE order_id = ? AND status <> 'OPEN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    /** Résolution admin (acte tracé, jamais automatique). */
    public void resolve(int orderId, String status, String note) throws SQLException {
        String sql = "UPDATE disputes SET status = ?, resolution_note = ?, resolved_at = NOW()"
            + " WHERE order_id = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, note == null ? null : note.substring(0, Math.min(500, note.length())));
            ps.setInt(3, orderId);
            ps.executeUpdate();
        }
    }

    public List<Dispute> findPaged(String status, int limit, int offset) throws SQLException {
        List<Dispute> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM disputes");
        if (status != null && !status.isEmpty()) sql.append(" WHERE status = ?");
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null && !status.isEmpty()) ps.setString(i++, status);
            ps.setInt(i++, Math.max(1, Math.min(100, limit)));
            ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Dispute mapRow(ResultSet rs) throws SQLException {
        Dispute d = new Dispute();
        d.setId(rs.getInt("id"));
        d.setOrderId(rs.getInt("order_id"));
        d.setPhone(rs.getString("phone"));
        d.setReason(rs.getString("reason"));
        d.setStatus(rs.getString("status"));
        d.setResolutionNote(rs.getString("resolution_note"));
        d.setResolvedAt(rs.getString("resolved_at"));
        d.setCreatedAt(rs.getString("created_at"));
        try { d.setUpdatedAt(rs.getString("updated_at")); } catch (SQLException ignored) {}
        return d;
    }
}
