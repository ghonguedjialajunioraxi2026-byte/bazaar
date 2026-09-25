package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActivityLogDAO {

    public void log(Integer adminId, String action, String entityType, Integer entityId, String details, String ip) throws SQLException {
        String sql = "INSERT INTO activity_logs (admin_id, action, entity_type, entity_id, details, ip_address) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (adminId != null) ps.setInt(1, adminId); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, action);
            ps.setString(3, entityType);
            if (entityId != null) ps.setInt(4, entityId); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, details);
            ps.setString(6, ip);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> findRecent(int limit) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT al.*, a.username FROM activity_logs al LEFT JOIN admins a ON al.admin_id = a.id ORDER BY al.created_at DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("adminId", rs.getInt("admin_id"));
                    row.put("username", rs.getString("username"));
                    row.put("action", rs.getString("action"));
                    row.put("entityType", rs.getString("entity_type"));
                    row.put("entityId", rs.getInt("entity_id"));
                    row.put("details", rs.getString("details"));
                    row.put("ip", rs.getString("ip_address"));
                    row.put("createdAt", rs.getString("created_at"));
                    list.add(row);
                }
            }
        }
        return list;
    }
}
