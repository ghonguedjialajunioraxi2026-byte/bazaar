package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Admin;
import com.ecommerce.util.PasswordUtil;

import java.sql.*;

public class AdminDAO {

    public Admin findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM admins WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /** Résolution d'identité serveur (jamais depuis le frontend). */
    public Admin findById(int id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    private Admin mapRow(ResultSet rs) throws SQLException {
        Admin a = new Admin();
        a.setId(rs.getInt("id"));
        a.setUsername(rs.getString("username"));
        try { a.setPasswordHash(rs.getString("password_hash")); } catch (SQLException ignored) {}
        try { a.setRole(rs.getString("role")); } catch (SQLException e) { a.setRole("ADMIN"); }
        if (a.getRole() == null) a.setRole("ADMIN");
        return a;
    }

    public void create(String username, String password) throws SQLException {
        String sql = "INSERT INTO admins (username, password_hash) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password));
            ps.executeUpdate();
        }
    }

    public boolean updatePassword(int id, String currentPlain, String newPlain) throws SQLException {
        Admin a = findById(id);
        if (a == null || a.getPasswordHash() == null) return false;
        if (!PasswordUtil.verify(currentPlain, a.getPasswordHash())) return false;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE admins SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, PasswordUtil.hash(newPlain));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }
}
