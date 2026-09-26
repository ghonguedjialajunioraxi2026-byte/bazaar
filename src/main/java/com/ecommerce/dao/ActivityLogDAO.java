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
        return findPaged(null, limit, 0);
    }

    /** Journal d'audit paginé + filtre action (Control Center). */
    public List<Map<String, Object>> findPaged(String action, int limit, int offset) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT al.*, a.username FROM activity_logs al "
            + "LEFT JOIN admins a ON al.admin_id = a.id");
        if (action != null && !action.trim().isEmpty()) sql.append(" WHERE al.action = ?");
        sql.append(" ORDER BY al.created_at DESC LIMIT ? OFFSET ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (action != null && !action.trim().isEmpty()) ps.setString(i++, action.trim());
            ps.setInt(i++, Math.max(1, Math.min(100, limit)));
            ps.setInt(i, Math.max(0, offset));
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

    public int count(String action) throws SQLException {
        String sql = "SELECT COUNT(*) FROM activity_logs"
            + (action != null && !action.trim().isEmpty() ? " WHERE action = ?" : "");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (action != null && !action.trim().isEmpty()) ps.setString(1, action.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
}
