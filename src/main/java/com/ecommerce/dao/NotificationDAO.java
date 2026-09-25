package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public int create(Notification n) throws SQLException {
        String sql = "INSERT INTO notifications (target_role, target_user_id, title, message, type, priority, link) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.getTargetRole());
            if (n.getTargetUserId() != null) ps.setInt(2, n.getTargetUserId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, n.getTitle());
            ps.setString(4, n.getMessage());
            ps.setString(5, n.getType() != null ? n.getType() : "SYSTEM");
            ps.setString(6, n.getPriority() != null ? n.getPriority() : "NORMAL");
            ps.setString(7, n.getLink());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public List<Notification> findForRole(String role) throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE target_role = ? OR target_role IS NULL ORDER BY created_at DESC LIMIT 50";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countUnread(String role) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE (target_role = ?) AND is_read = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void markRead(int id) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void markUnread(int id) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 0 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Liste filtrée + paginée (centre de notifications).
     * @param type filtre type (ORDER, STOCK, ...) ou null
     * @param unreadOnly true = non lues, false = lues, null = toutes
     * @param query recherche libre titre/message ou null
     */
    public List<Notification> findFiltered(String role, String type, Boolean unreadOnly, String query, int limit, int offset) throws SQLException {
        List<Notification> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM notifications WHERE (target_role = ? OR target_role IS NULL)");
        List<Object> params = new ArrayList<>();
        params.add(role);
        if (type != null && !type.isEmpty()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (unreadOnly != null) {
            sql.append(unreadOnly ? " AND is_read = 0" : " AND is_read = 1");
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (title LIKE ? OR message LIKE ?)");
            String like = "%" + query.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY is_read ASC, created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countFiltered(String role, String type, Boolean unreadOnly, String query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM notifications WHERE (target_role = ? OR target_role IS NULL)");
        List<Object> params = new ArrayList<>();
        params.add(role);
        if (type != null && !type.isEmpty()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (unreadOnly != null) {
            sql.append(unreadOnly ? " AND is_read = 0" : " AND is_read = 1");
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (title LIKE ? OR message LIKE ?)");
            String like = "%" + query.trim() + "%";
            params.add(like);
            params.add(like);
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Supprime toutes les notifications LUES d'un rôle (purge). */
    public int deleteRead(String role) throws SQLException {
        String sql = "DELETE FROM notifications WHERE (target_role = ? OR target_role IS NULL) AND is_read = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            return ps.executeUpdate();
        }
    }

    /** Suppression multiple par ids (ids vérifiés côté appelant). */
    public int deleteByIds(String role, List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }
        String sql = "DELETE FROM notifications WHERE (target_role = ? OR target_role IS NULL) AND id IN (" + in + ")";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 2, ids.get(i));
            return ps.executeUpdate();
        }
    }

    /** Marque plusieurs notifications comme lues. */
    public int markManyRead(String role, List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }
        String sql = "UPDATE notifications SET is_read = 1 WHERE (target_role = ? OR target_role IS NULL) AND id IN (" + in + ")";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 2, ids.get(i));
            return ps.executeUpdate();
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
            else ps.setString(i + 1, String.valueOf(p));
        }
    }

    public void markAllRead(String role) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE target_role = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM notifications WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void notifyNewOrder(int orderId, String orderNumber, String clientName) throws SQLException {
        Notification n = new Notification();
        n.setTargetRole("SUPER_ADMIN");
        n.setTitle("Nouvelle commande reçue");
        n.setMessage("Commande " + orderNumber + " de " + clientName);
        n.setType("ORDER");
        n.setLink("/api/orders?action=find&id=" + orderId);
        create(n);
        // Also notify ADMIN and GESTIONNAIRE
        n.setTargetRole("ADMIN");
        create(n);
        n.setTargetRole("GESTIONNAIRE");
        create(n);
    }

    public void notifyLowStock(String productName, int stock) throws SQLException {
        Notification n = new Notification();
        n.setTargetRole("SUPER_ADMIN");
        n.setTitle("Stock faible : " + productName);
        n.setMessage("Il ne reste que " + stock + " unité(s) en stock pour " + productName);
        n.setType("STOCK");
        create(n);
    }

    public void notifySupplierRequest(String supplierName) throws SQLException {
        Notification n = new Notification();
        n.setTargetRole("SUPER_ADMIN");
        n.setTitle("Nouvelle demande fournisseur");
        n.setMessage("Nouvelle demande de " + supplierName);
        n.setType("SUPPLIER");
        create(n);
    }

    public void notifyPayment(String orderNumber, int amount, String status) throws SQLException {
        Notification n = new Notification();
        n.setTargetRole("SUPER_ADMIN");
        n.setTitle("Paiement " + status.toLowerCase());
        n.setMessage("Paiement de " + amount + " FCFA pour la commande " + orderNumber);
        n.setType("PAYMENT");
        create(n);
    }

    // ================= FOURNISSEUR =================

    public List<Notification> findForSupplier(int supplierId) throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL) ORDER BY created_at DESC LIMIT 60";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countUnreadForSupplier(int supplierId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL) AND is_read = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void markReadForSupplier(int supplierId, int id) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ? AND target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, supplierId);
            ps.executeUpdate();
        }
    }

    public void markAllReadForSupplier(int supplierId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.executeUpdate();
        }
    }

    public void createForSupplier(int supplierId, String title, String message, String type) throws SQLException {
        Notification n = new Notification();
        n.setTargetRole("SUPPLIER");
        n.setTargetUserId(supplierId);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type == null ? "SYSTEM" : type);
        create(n);
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("id"));
        n.setTargetRole(rs.getString("target_role"));
        int uid = rs.getInt("target_user_id");
        n.setTargetUserId(rs.wasNull() ? null : uid);
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setType(rs.getString("type"));
        try { n.setPriority(rs.getString("priority")); } catch (SQLException ignored) { n.setPriority("NORMAL"); }
        n.setRead(rs.getBoolean("is_read"));
        n.setLink(rs.getString("link"));
        n.setCreatedAt(rs.getString("created_at"));
        return n;
    }
}
