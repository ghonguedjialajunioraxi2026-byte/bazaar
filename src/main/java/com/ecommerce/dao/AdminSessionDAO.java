package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;
import java.util.UUID;

public class AdminSessionDAO {

    public String createSession(int adminId, String ip, String userAgent, int expiresMinutes) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO admin_sessions (admin_id, token, ip_address, user_agent, expires_at) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            ps.setString(2, token);
            ps.setString(3, ip);
            ps.setString(4, userAgent);
            ps.setInt(5, expiresMinutes);
            ps.executeUpdate();
        }
        return token;
    }

    public int validateSession(String token) throws SQLException {
        if (token == null || token.isEmpty()) return 0;
        String sql = "SELECT admin_id FROM admin_sessions WHERE token = ? AND expires_at > NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("admin_id");
            }
        }
        return 0;
    }

    public void invalidateSession(String token) throws SQLException {
        String sql = "DELETE FROM admin_sessions WHERE token = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    public void invalidateAllForAdmin(int adminId) throws SQLException {
        String sql = "DELETE FROM admin_sessions WHERE admin_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            ps.executeUpdate();
        }
    }

    public void cleanupExpired() throws SQLException {
        String sql = "DELETE FROM admin_sessions WHERE expires_at <= NOW()";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
