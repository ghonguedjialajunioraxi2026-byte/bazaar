package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Supplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierDAO {

    public List<Supplier> findAll() throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers ORDER BY name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Supplier> findActive() throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers WHERE is_active = 1 ORDER BY name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Supplier findById(int id) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Supplier findByEmail(String email) throws SQLException {
        if (email == null || email.isEmpty()) return null;
        String sql = "SELECT * FROM suppliers WHERE email = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Supplier findByGoogleId(String googleId) throws SQLException {
        if (googleId == null || googleId.isEmpty()) return null;
        String sql = "SELECT * FROM suppliers WHERE google_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public int create(Supplier s) throws SQLException {
        String sql = "INSERT INTO suppliers (name, phone, whatsapp, email, password_hash, contact_name, logo, description, city, address, is_active, commission_rate, google_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getWhatsapp());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getPasswordHash());
            ps.setString(6, s.getContactName());
            ps.setString(7, s.getLogo());
            ps.setString(8, s.getDescription());
            ps.setString(9, s.getCity());
            ps.setString(10, s.getAddress());
            ps.setBoolean(11, s.isActive());
            ps.setDouble(12, s.getCommissionRate());
            ps.setString(13, s.getGoogleId());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public void update(Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET name=?, phone=?, whatsapp=?, email=?, contact_name=?, logo=?, description=?, city=?, address=?, is_active=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getWhatsapp());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getContactName());
            ps.setString(6, s.getLogo());
            ps.setString(7, s.getDescription());
            ps.setString(8, s.getCity());
            ps.setString(9, s.getAddress());
            ps.setBoolean(10, s.isActive());
            ps.setInt(11, s.getId());
            ps.executeUpdate();
        }
    }

    public void updateProfile(int id, Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET "
                + "name=?, phone=?, whatsapp=?, email=?, contact_name=?, city=?, address=?, description=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getWhatsapp());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getContactName());
            ps.setString(6, s.getCity());
            ps.setString(7, s.getAddress());
            ps.setString(8, s.getDescription());
            ps.setInt(9, id);
            ps.executeUpdate();
        }
    }

    /** Coordonnées de versement Mobile Money (choisies par le fournisseur). */
    public void updatePayoutDetails(int id, String provider, String phone) throws SQLException {
        String sql = "UPDATE suppliers SET payout_provider = ?, payout_phone = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, provider);
            ps.setString(2, phone);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Numéros de versement MTN + Orange (déjà normalisés par l'appelant ; NULL = conservé). */
    public void updatePayoutPhones(int id, String mtnPhone, String orangePhone) throws SQLException {
        String sql = "UPDATE suppliers SET mtn_phone = COALESCE(?, mtn_phone),"
            + " orange_phone = COALESCE(?, orange_phone) WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mtnPhone);
            ps.setString(2, orangePhone);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void updateLogo(int id, String logo) throws SQLException {        String sql = "UPDATE suppliers SET logo = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, logo);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void updatePassword(int id, String passwordHash) throws SQLException {
        String sql = "UPDATE suppliers SET password_hash = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void linkGoogleAccount(int id, String googleId, String logo) throws SQLException {
        String sql = "UPDATE suppliers SET google_id = ?, logo = COALESCE(logo, ?) WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleId);
            ps.setString(2, logo);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void updateLastLogin(int id) throws SQLException {
        String sql = "UPDATE suppliers SET last_login = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM suppliers WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void updateStatus(int id, boolean active) throws SQLException {
        String sql = "UPDATE suppliers SET is_active = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setPhone(rs.getString("phone"));
        s.setWhatsapp(rs.getString("whatsapp"));
        s.setEmail(rs.getString("email"));
        try { s.setPasswordHash(rs.getString("password_hash")); } catch (SQLException ignored) {}
        try { s.setContactName(rs.getString("contact_name")); } catch (SQLException ignored) {}
        try { s.setLogo(rs.getString("logo")); } catch (SQLException ignored) {}
        try { s.setDescription(rs.getString("description")); } catch (SQLException ignored) {}
        s.setCity(rs.getString("city"));
        s.setAddress(rs.getString("address"));
        s.setActive(rs.getBoolean("is_active"));
        try { s.setCommissionRate(rs.getDouble("commission_rate")); } catch (SQLException ignored) {}
        try { s.setBalance(rs.getInt("balance")); } catch (SQLException ignored) {}
        try { s.setPendingBalance(rs.getInt("pending_balance")); } catch (SQLException ignored) {}
        try { s.setPaidBalance(rs.getInt("paid_balance")); } catch (SQLException ignored) {}
        try { s.setLastLogin(rs.getString("last_login")); } catch (SQLException ignored) {}
        try { s.setGoogleId(rs.getString("google_id")); } catch (SQLException ignored) {}
        try { s.setPayoutProvider(rs.getString("payout_provider")); } catch (SQLException ignored) {}
        try { s.setPayoutPhone(rs.getString("payout_phone")); } catch (SQLException ignored) {}
        try { s.setMtnPhone(rs.getString("mtn_phone")); } catch (SQLException ignored) {}
        try { s.setOrangePhone(rs.getString("orange_phone")); } catch (SQLException ignored) {}
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    }
}
