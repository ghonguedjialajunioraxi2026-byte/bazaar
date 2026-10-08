package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.PaymentEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Journal des callbacks PawaPay. L'unicité de {@code event_key}
 * (depositId + statut) garantit qu'un webhook répété ne provoque jamais
 * deux validations financières.
 */
public class PaymentEventDAO {

    /**
     * Enregistre l'événement. Retourne true si nouveau (à traiter),
     * false si déjà vu (doublon → ne rien recompter).
     */
    public boolean insertIfNew(int paymentId, String eventKey, String payloadJson) throws SQLException {
        String sql = "INSERT INTO payment_events (payment_id, event_key, payload) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            ps.setString(2, eventKey);
            ps.setString(3, payloadJson);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isDuplicateKey(e)) return false;
            throw e;
        }
    }

    public PaymentEvent findByEventKey(String eventKey) throws SQLException {
        String sql = "SELECT * FROM payment_events WHERE event_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PaymentEvent ev = new PaymentEvent();
                    ev.setId(rs.getInt("id"));
                    ev.setPaymentId(rs.getInt("payment_id"));
                    ev.setEventKey(rs.getString("event_key"));
                    try { ev.setPayload(rs.getString("payload")); } catch (SQLException ignored) {}
                    try { ev.setReceivedAt(rs.getString("received_at")); } catch (SQLException ignored) {}
                    return ev;
                }
            }
        }
        return null;
    }

    private static boolean isDuplicateKey(SQLException e) {
        if (!"23000".equals(e.getSQLState())) return false;
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("uq_payment_event") || m.contains("event_key") || m.contains("Duplicate");
    }
}
