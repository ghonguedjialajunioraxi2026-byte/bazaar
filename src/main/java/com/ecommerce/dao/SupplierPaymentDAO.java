package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.SupplierPayment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierPaymentDAO {

    public List<SupplierPayment> findBySupplier(int supplierId) throws SQLException {
        List<SupplierPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM supplier_payments WHERE supplier_id = ? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int create(SupplierPayment sp) throws SQLException {
        String sql = "INSERT INTO supplier_payments (supplier_id, reference, amount, method, status, notes) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sp.getSupplierId());
            ps.setString(2, sp.getReference());
            ps.setInt(3, sp.getAmount());
            ps.setString(4, sp.getMethod());
            ps.setString(5, sp.getStatus() == null ? "PENDING" : sp.getStatus());
            ps.setString(6, sp.getNotes());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public void updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE supplier_payments SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // total demandé (hors FAILED) pour un fournisseur
    public int sumRequested(int supplierId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE supplier_id = ? AND status <> 'FAILED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private SupplierPayment mapRow(ResultSet rs) throws SQLException {
        SupplierPayment sp = new SupplierPayment();
        sp.setId(rs.getInt("id"));
        sp.setSupplierId(rs.getInt("supplier_id"));
        sp.setReference(rs.getString("reference"));
        sp.setAmount(rs.getInt("amount"));
        sp.setMethod(rs.getString("method"));
        sp.setStatus(rs.getString("status"));
        sp.setNotes(rs.getString("notes"));
        sp.setCreatedAt(rs.getString("created_at"));
        sp.setUpdatedAt(rs.getString("updated_at"));
        return sp;
    }
}
