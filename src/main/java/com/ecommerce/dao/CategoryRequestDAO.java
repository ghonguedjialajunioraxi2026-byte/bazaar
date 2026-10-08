package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.CategoryRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Demandes de catégories des fournisseurs (file de validation admin). */
public class CategoryRequestDAO {

    public int create(int supplierId, String name, Integer parentId) throws SQLException {
        String sql = "INSERT INTO category_requests (supplier_id, name, parent_id, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, supplierId);
            ps.setString(2, name);
            if (parentId != null && parentId > 0) ps.setInt(3, parentId);
            else ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public boolean existsPending(int supplierId, String name, Integer parentId) throws SQLException {
        String want = name == null ? "" : name.trim().toLowerCase();
        String sql2 = "SELECT COUNT(*) FROM category_requests WHERE supplier_id = ? AND status = 'PENDING'"
            + " AND LOWER(TRIM(name)) = ?"
            + (parentId != null && parentId > 0 ? " AND parent_id = ?" : " AND parent_id IS NULL");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql2)) {
            ps.setInt(1, supplierId);
            ps.setString(2, want);
            if (parentId != null && parentId > 0) ps.setInt(3, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public List<CategoryRequest> findBySupplier(int supplierId) throws SQLException {
        List<CategoryRequest> list = new ArrayList<>();
        String sql = "SELECT r.*, c.name AS parent_name FROM category_requests r "
            + "LEFT JOIN categories c ON c.id = r.parent_id "
            + "WHERE r.supplier_id = ? ORDER BY r.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<CategoryRequest> findByStatus(String status, int limit) throws SQLException {
        List<CategoryRequest> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT r.*, s.name AS supplier_name, c.name AS parent_name FROM category_requests r "
            + "LEFT JOIN suppliers s ON s.id = r.supplier_id "
            + "LEFT JOIN categories c ON c.id = r.parent_id");
        if (status != null && !status.isEmpty()) sql.append(" WHERE r.status = ?");
        sql.append(" ORDER BY r.created_at DESC LIMIT ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null && !status.isEmpty()) ps.setString(i++, status);
            ps.setInt(i, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CategoryRequest r = mapRow(rs);
                    try { r.setSupplierName(rs.getString("supplier_name")); } catch (SQLException ignored) {}
                    list.add(r);
                }
            }
        }
        return list;
    }

    public CategoryRequest findById(int id) throws SQLException {
        String sql = "SELECT r.*, c.name AS parent_name FROM category_requests r "
            + "LEFT JOIN categories c ON c.id = r.parent_id WHERE r.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public boolean resolve(int id, String status, String adminNote) throws SQLException {
        String sql = "UPDATE category_requests SET status = ?, admin_note = ? WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, adminNote);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    private CategoryRequest mapRow(ResultSet rs) throws SQLException {
        CategoryRequest r = new CategoryRequest();
        r.setId(rs.getInt("id"));
        r.setSupplierId(rs.getInt("supplier_id"));
        r.setName(rs.getString("name"));
        int pid = rs.getInt("parent_id");
        r.setParentId(rs.wasNull() ? null : pid);
        try { r.setParentName(rs.getString("parent_name")); } catch (SQLException ignored) {}
        r.setStatus(rs.getString("status"));
        try { r.setAdminNote(rs.getString("admin_note")); } catch (SQLException ignored) {}
        r.setCreatedAt(rs.getString("created_at"));
        return r;
    }
}
