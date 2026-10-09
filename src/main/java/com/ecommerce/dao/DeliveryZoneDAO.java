package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.DeliveryZone;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DeliveryZoneDAO {

    public List<DeliveryZone> findAll() throws SQLException {
        List<DeliveryZone> list = new ArrayList<>();
        String sql = "SELECT * FROM delivery_zones ORDER BY city, zone";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public int getFee(String city) throws SQLException {
        String sql = "SELECT fee FROM delivery_zones WHERE city = ? AND is_active = 1 LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("fee");
            }
        }
        return 3500;
    }

    public int create(DeliveryZone dz) throws SQLException {
        String sql = "INSERT INTO delivery_zones (city, zone, fee, is_active) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, dz.getCity());
            ps.setString(2, dz.getZone());
            ps.setInt(3, dz.getFee());
            ps.setBoolean(4, dz.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    public void update(DeliveryZone dz) throws SQLException {
        String sql = "UPDATE delivery_zones SET city=?, zone=?, fee=?, is_active=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dz.getCity());
            ps.setString(2, dz.getZone());
            ps.setInt(3, dz.getFee());
            ps.setBoolean(4, dz.isActive());
            ps.setInt(5, dz.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM delivery_zones WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private DeliveryZone mapRow(ResultSet rs) throws SQLException {
        DeliveryZone dz = new DeliveryZone();
        dz.setId(rs.getInt("id"));
        dz.setCity(rs.getString("city"));
        dz.setZone(rs.getString("zone"));
        dz.setFee(rs.getInt("fee"));
        dz.setActive(rs.getBoolean("is_active"));
        return dz;
    }
}
