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
                    Admin a = new Admin();
                    a.setId(rs.getInt("id"));
                    a.setUsername(rs.getString("username"));
                    a.setPasswordHash(rs.getString("password_hash"));
                    try { a.setRole(rs.getString("role")); } catch (SQLException e) { a.setRole("ADMIN"); }
                    return a;
                }
            }
        }
        return null;
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
}
