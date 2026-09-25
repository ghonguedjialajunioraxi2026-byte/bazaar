package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Payment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public int create(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, amount, currency, transaction_ref, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod() != null ? p.getMethod() : "WHATSAPP");
            ps.setInt(3, p.getAmount());
            ps.setString(4, p.getCurrency() != null ? p.getCurrency() : "FCFA");
            ps.setString(5, p.getTransactionRef());
            ps.setString(6, p.getStatus() != null ? p.getStatus() : "EN_ATTENTE");
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public Payment findById(int id) throws SQLException {
        String sql = "SELECT * FROM payments WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Payment findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Payment> findAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public void updateStatus(int id, String status, String transactionRef) throws SQLException {
        String sql = "UPDATE payments SET status = ?, transaction_ref = ?, paid_at = IF(?='REUSSIE',NOW(),NULL) WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, transactionRef);
            ps.setString(3, status);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public int totalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'REUSSIE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public List<Payment> findByStatus(String status) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE status = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getInt("id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setMethod(rs.getString("method"));
        p.setAmount(rs.getInt("amount"));
        p.setCurrency(rs.getString("currency"));
        p.setTransactionRef(rs.getString("transaction_ref"));
        p.setStatus(rs.getString("status"));
        try { p.setPaidAt(rs.getString("paid_at")); } catch (SQLException e) { p.setPaidAt(null); }
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}
