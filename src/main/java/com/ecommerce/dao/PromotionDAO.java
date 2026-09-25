package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Promotion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PromotionDAO {

    public int create(Promotion p) throws SQLException {
        String sql = "INSERT INTO promotions (product_id, promo_price, promo_percent, start_date, end_date, is_active) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getProductId());
            ps.setInt(2, p.getPromoPrice());
            ps.setInt(3, p.getPromoPercent());
            ps.setString(4, p.getStartDate());
            ps.setString(5, p.getEndDate());
            ps.setBoolean(6, p.isActive());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public void update(Promotion p) throws SQLException {
        String sql = "UPDATE promotions SET promo_price=?, promo_percent=?, start_date=?, end_date=?, is_active=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getPromoPrice());
            ps.setInt(2, p.getPromoPercent());
            ps.setString(3, p.getStartDate());
            ps.setString(4, p.getEndDate());
            ps.setBoolean(5, p.isActive());
            ps.setInt(6, p.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM promotions WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<Promotion> findAll() throws SQLException {
        List<Promotion> list = new ArrayList<>();
        String sql = "SELECT * FROM promotions ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Promotion> findActive() throws SQLException {
        List<Promotion> list = new ArrayList<>();
        String sql = "SELECT * FROM promotions WHERE is_active=1 AND CURDATE() BETWEEN start_date AND end_date";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Promotion findByProductId(int productId) throws SQLException {
        String sql = "SELECT * FROM promotions WHERE product_id = ? AND is_active=1 AND CURDATE() BETWEEN start_date AND end_date ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    private Promotion mapRow(ResultSet rs) throws SQLException {
        Promotion p = new Promotion();
        p.setId(rs.getInt("id"));
        p.setProductId(rs.getInt("product_id"));
        p.setPromoPrice(rs.getInt("promo_price"));
        p.setPromoPercent(rs.getInt("promo_percent"));
        p.setStartDate(rs.getString("start_date"));
        p.setEndDate(rs.getString("end_date"));
        p.setActive(rs.getBoolean("is_active"));
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}
