package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;
import java.util.UUID;

/** Sessions client : même pattern que supplier/admin_sessions (Bearer 48 hex). */
public class CustomerSessionDAO {

    public String createSession(int customerId, String ip, String userAgent, int expiresHours) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO customer_sessions (customer_id, token, ip_address, user_agent, expires_at) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, token);
            ps.setString(3, ip);
            ps.setString(4, userAgent);
            ps.setInt(5, expiresHours);
            ps.executeUpdate();
        }
        return token;
    }

    public int validateSession(String token) throws SQLException {
        if (token == null || token.isEmpty()) return 0;
        String sql = "SELECT customer_id FROM customer_sessions WHERE token = ? AND expires_at > NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("customer_id");
            }
        }
        return 0;
    }

    public void invalidateSession(String token) throws SQLException {
        if (token == null || token.isEmpty()) return;
        String sql = "DELETE FROM customer_sessions WHERE token = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    public void invalidateAllForCustomer(int customerId) throws SQLException {
        String sql = "DELETE FROM customer_sessions WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        }
    }
}
