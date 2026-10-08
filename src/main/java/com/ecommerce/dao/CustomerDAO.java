package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Customer;

import java.sql.*;

/** Comptes clients légers (nom + email, Google sub optionnel). */
public class CustomerDAO {

    public Customer findById(int id) throws SQLException {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Customer findByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) return null;
        String sql = "SELECT * FROM customers WHERE email = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Customer findByGoogleSub(String sub) throws SQLException {
        if (sub == null || sub.isEmpty()) return null;
        String sql = "SELECT * FROM customers WHERE google_sub = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sub);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public int create(String name, String email) throws SQLException {
        return create(name, email, null);
    }

    /**
     * Création avec avatar optionnel. Si la colonne avatar n'existe pas
     * encore (migration non appliquée), repli silencieux sans avatar :
     * la création du compte ne doit jamais échouer pour une photo.
     */
    public int create(String name, String email, String avatar) throws SQLException {
        String cleanAvatar = (avatar == null || avatar.trim().isEmpty()
            || !com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(avatar)) ? null : avatar.trim();
        if (cleanAvatar != null) {
            try {
                String sql = "INSERT INTO customers (name, email, avatar) VALUES (?, ?, ?)";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim().substring(0, Math.min(120, name.trim().length())));
                    ps.setString(2, email.trim().toLowerCase());
                    ps.setString(3, cleanAvatar.substring(0, Math.min(500, cleanAvatar.length())));
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) {
                        if (k.next()) return k.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                // Colonne absente ou autre : repli sans avatar.
            }
        }
        String sql = "INSERT INTO customers (name, email) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim().substring(0, Math.min(120, name.trim().length())));
            ps.setString(2, email.trim().toLowerCase());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public int createWithGoogle(String name, String email, String googleSub) throws SQLException {
        return createWithGoogle(name, email, googleSub, null);
    }

    public int createWithGoogle(String name, String email, String googleSub, String avatar) throws SQLException {
        String cleanAvatar = (avatar == null || avatar.trim().isEmpty()
            || !com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(avatar)) ? null : avatar.trim();
        if (cleanAvatar != null) {
            try {
                String sql = "INSERT INTO customers (name, email, google_sub, avatar) VALUES (?, ?, ?, ?)";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name == null || name.trim().isEmpty() ? email.trim() : name.trim());
                    ps.setString(2, email.trim().toLowerCase());
                    ps.setString(3, googleSub);
                    ps.setString(4, cleanAvatar.substring(0, Math.min(500, cleanAvatar.length())));
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) {
                        if (k.next()) return k.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                // Colonne absente : repli sans avatar.
            }
        }
        String sql = "INSERT INTO customers (name, email, google_sub) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name == null || name.trim().isEmpty() ? email.trim() : name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, googleSub);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public void linkGoogle(int id, String googleSub) throws SQLException {
        String sql = "UPDATE customers SET google_sub = ? WHERE id = ? AND google_sub IS NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleSub);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void updateName(int id, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return;
        String sql = "UPDATE customers SET name = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim().substring(0, Math.min(120, name.trim().length())));
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Photo de profil (facultative). URL validée, 500 car. max.
     * Silencieux si la colonne n'existe pas encore (migration non appliquée).
     */
    public void updateAvatar(int id, String avatar) throws SQLException {
        if (avatar == null || avatar.trim().isEmpty()
                || !com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(avatar)) return;
        String sql = "UPDATE customers SET avatar = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String clean = avatar.trim();
            ps.setString(1, clean.substring(0, Math.min(500, clean.length())));
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Colonne absente : la photo reste gérée côté affichage (visuel réservé).
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setEmail(rs.getString("email"));
        try { c.setGoogleSub(rs.getString("google_sub")); } catch (SQLException ignored) {}
        try { c.setAvatar(rs.getString("avatar")); } catch (SQLException ignored) {}
        c.setCreatedAt(rs.getString("created_at"));
        return c;
    }
}
