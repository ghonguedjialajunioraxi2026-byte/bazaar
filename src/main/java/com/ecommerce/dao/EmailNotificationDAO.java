package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.EmailNotification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** File d'emails : mise en file, marquage envoyé/échoué, rejeu. */
public class EmailNotificationDAO {

    public int enqueue(EmailNotification e) throws SQLException {
        String sql = "INSERT INTO email_notifications"
            + " (recipient, subject, body, event_code, order_id, status)"
            + " VALUES (?, ?, ?, ?, ?, 'QUEUED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getRecipient());
            ps.setString(2, e.getSubject());
            ps.setString(3, e.getBody());
            ps.setString(4, e.getEventCode() != null ? e.getEventCode() : "GENERIC");
            if (e.getOrderId() != null) ps.setInt(5, e.getOrderId());
            else ps.setNull(5, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    public List<EmailNotification> findQueued(int limit) throws SQLException {
        List<EmailNotification> list = new ArrayList<>();
        String sql = "SELECT * FROM email_notifications WHERE status = 'QUEUED'"
            + " ORDER BY created_at ASC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void markSent(int id) throws SQLException {
        String sql = "UPDATE email_notifications SET status = 'SENT', sent_at = NOW(),"
            + " attempts = attempts + 1, last_error = NULL WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void markFailed(int id, String error) throws SQLException {
        String safe = error == null ? null : error.substring(0, Math.min(255, error.length()));
        String sql = "UPDATE email_notifications SET status = 'FAILED',"
            + " attempts = attempts + 1, last_error = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safe);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Remet un email échoué en file (rejeu admin, idempotent par statut). */
    public boolean requeue(int id) throws SQLException {
        String sql = "UPDATE email_notifications SET status = 'QUEUED'"
            + " WHERE id = ? AND status = 'FAILED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private EmailNotification mapRow(ResultSet rs) throws SQLException {
        EmailNotification e = new EmailNotification();
        e.setId(rs.getInt("id"));
        e.setRecipient(rs.getString("recipient"));
        e.setSubject(rs.getString("subject"));
        e.setBody(rs.getString("body"));
        try { e.setEventCode(rs.getString("event_code")); } catch (SQLException ignored) {}
        try {
            int oid = rs.getInt("order_id");
            e.setOrderId(rs.wasNull() ? null : oid);
        } catch (SQLException ignored) {}
        e.setStatus(rs.getString("status"));
        try { e.setAttempts(rs.getInt("attempts")); } catch (SQLException ignored) {}
        try { e.setLastError(rs.getString("last_error")); } catch (SQLException ignored) {}
        try { e.setCreatedAt(rs.getString("created_at")); } catch (SQLException ignored) {}
        try { e.setSentAt(rs.getString("sent_at")); } catch (SQLException ignored) {}
        return e;
    }
}
