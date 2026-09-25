package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class ProductImageDAO {

    public void syncImages(int productId, List<String> imageUrls) throws SQLException {
        String deleteSql = "DELETE FROM product_images WHERE product_id = ?";
        String insertSql = "INSERT INTO product_images (product_id, image_url, is_primary, sort_order) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psDel = conn.prepareStatement(deleteSql)) {
                psDel.setInt(1, productId);
                psDel.executeUpdate();
            }
            try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < imageUrls.size(); i++) {
                    psIns.setInt(1, productId);
                    psIns.setString(2, imageUrls.get(i));
                    psIns.setBoolean(3, i == 0);
                    psIns.setInt(4, i);
                    psIns.addBatch();
                }
                psIns.executeBatch();
            }
            conn.commit();
        }
    }

    public List<String> findByProduct(int productId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT image_url FROM product_images WHERE product_id = ? ORDER BY sort_order";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString("image_url"));
            }
        }
        return list;
    }

    public List<Map<String, Object>> findDetailsByProduct(int productId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, image_url, is_primary, sort_order FROM product_images WHERE product_id = ? ORDER BY sort_order";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> img = new LinkedHashMap<>();
                    img.put("id", rs.getInt("id"));
                    img.put("url", rs.getString("image_url"));
                    img.put("isPrimary", rs.getBoolean("is_primary"));
                    img.put("sortOrder", rs.getInt("sort_order"));
                    list.add(img);
                }
            }
        }
        return list;
    }

    public void addImage(int productId, String url, boolean isPrimary) throws SQLException {
        String sql = "INSERT INTO product_images (product_id, image_url, is_primary, sort_order) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, url);
            ps.setBoolean(3, isPrimary);
            ps.setInt(4, getMaxSortOrder(productId) + 1);
            ps.executeUpdate();
        }
    }

    public void removeImage(int imageId) throws SQLException {
        String sql = "DELETE FROM product_images WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, imageId);
            ps.executeUpdate();
        }
    }

    public void setPrimary(int productId, int imageId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.prepareStatement("UPDATE product_images SET is_primary = 0 WHERE product_id = ?").executeUpdate();
            try (PreparedStatement ps = conn.prepareStatement("UPDATE product_images SET is_primary = 1 WHERE id = ? AND product_id = ?")) {
                ps.setInt(1, imageId);
                ps.setInt(2, productId);
                ps.executeUpdate();
            }
        }
    }

    private int getMaxSortOrder(int productId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sort_order),-1) FROM product_images WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }
}
