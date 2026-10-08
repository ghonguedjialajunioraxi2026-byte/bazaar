package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;
import java.util.UUID;

public class SupplierSessionDAO {

    public String createSession(int supplierId, String ip, String userAgent, int expiresHours) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO supplier_sessions (supplier_id, token, ip_address, user_agent, expires_at) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? HOUR))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
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
        String sql = "SELECT supplier_id FROM supplier_sessions WHERE token = ? AND expires_at > NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("supplier_id");
            }
        }
        return 0;
    }

    /**
     * Renouvellement glissant : chaque requête authentifiée repousse
     * l'expiration (reste connecté tant que le compte est actif ;
     * seule une déconnexion explicite invalide la session).
     */
    public void refreshSession(String token, int expiresHours) throws SQLException {
        if (token == null || token.isEmpty()) return;
        String sql = "UPDATE supplier_sessions SET expires_at = DATE_ADD(NOW(), INTERVAL ? HOUR) WHERE token = ? AND expires_at > NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expiresHours);
            ps.setString(2, token);
            ps.executeUpdate();
        }
    }

    public void invalidateSession(String token) throws SQLException {
        if (token == null || token.isEmpty()) return;
        String sql = "DELETE FROM supplier_sessions WHERE token = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    public void invalidateAllForSupplier(int supplierId) throws SQLException {
        String sql = "DELETE FROM supplier_sessions WHERE supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.executeUpdate();
        }
    }
}
