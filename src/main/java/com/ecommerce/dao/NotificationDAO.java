package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public int create(Notification n) throws SQLException {
        return createFull(n);
    }

    /** Insertion complète (colonnes v3 si présentes, sinon repli v2). */
    public int createFull(Notification n) throws SQLException {
        boolean v3 = hasColumn("event_code");
        String sql = v3
            ? "INSERT INTO notifications (target_role, target_user_id, title, message, type, priority, link, event_code, category, entity_type, entity_id, action_url, dedup_key, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            : "INSERT INTO notifications (target_role, target_user_id, title, message, type, priority, link) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.getTargetRole());
            if (n.getTargetUserId() != null) ps.setInt(2, n.getTargetUserId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, n.getTitle());
            ps.setString(4, n.getMessage());
            ps.setString(5, n.getType() != null ? n.getType() : "SYSTEM");
            ps.setString(6, n.getPriority() != null ? n.getPriority() : "NORMAL");
            ps.setString(7, n.getLink());
            if (v3) {
                ps.setString(8, n.getEventCode());
                ps.setString(9, n.getCategory() != null ? n.getCategory() : n.getType());
                ps.setString(10, n.getEntityType());
                if (n.getEntityId() != null) ps.setInt(11, n.getEntityId()); else ps.setNull(11, Types.INTEGER);
                ps.setString(12, n.getActionUrl() != null ? n.getActionUrl() : n.getLink());
                if (n.getDedupKey() != null && !n.getDedupKey().isEmpty()) ps.setString(13, n.getDedupKey());
                else ps.setNull(13, Types.VARCHAR);
                ps.setString(14, n.getMetadata());
            }
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    /** Idempotence : retrouve l'id existant pour une clé logique. */
    public Integer findIdByDedup(String dedupKey) throws SQLException {
        if (dedupKey == null || dedupKey.isEmpty() || !hasColumn("dedup_key")) return null;
        String sql = "SELECT id FROM notifications WHERE dedup_key = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dedupKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    private volatile Boolean v3cache = null;

    private boolean hasColumn(String column) {
        if (v3cache != null && !"event_code".equals(column)) {
            // Cache uniquement pour event_code (présence globale v3) ; dedup_key suit le même sort.
            return v3cache;
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = ?")) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = rs.next() && rs.getInt(1) > 0;
                if ("event_code".equals(column)) v3cache = has;
                return has;
            }
        } catch (SQLException e) {
            return false;
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
        markReadScoped(null, id);
    }

    /**
     * Marque comme lu EN VÉRIFIANT le périmètre (anti-IDOR).
     * @param role rôle propriétaire, ou null pour usage interne déjà vérifié (legacy).
     * @return true si une ligne du périmètre a été touchée.
     */
    public boolean markReadScoped(String role, int id) throws SQLException {
        String sql = role == null
            ? "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = ?"
            : "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = ? AND (target_role = ? OR target_role IS NULL)";
        // read_at peut ne pas exister avant migration v3 : repli silencieux.
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (role != null) ps.setString(2, role);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("read_at")) {
                String fallback = role == null
                    ? "UPDATE notifications SET is_read = 1 WHERE id = ?"
                    : "UPDATE notifications SET is_read = 1 WHERE id = ? AND (target_role = ? OR target_role IS NULL)";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(fallback)) {
                    ps.setInt(1, id);
                    if (role != null) ps.setString(2, role);
                    return ps.executeUpdate() > 0;
                }
            }
            throw e;
        }
    }

    public boolean markUnreadScoped(String role, int id) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 0 WHERE id = ? AND (target_role = ? OR target_role IS NULL)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, role);
            return ps.executeUpdate() > 0;
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
            sql.append(" AND (`type` = ?").append(hasColumn("event_code") ? " OR category = ?" : "").append(")");
            params.add(type);
            if (hasColumn("event_code")) params.add(type);
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
            sql.append(" AND (`type` = ?").append(hasColumn("event_code") ? " OR category = ?" : "").append(")");
            params.add(type);
            if (hasColumn("event_code")) params.add(type);
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

    /** Suppression anti-IDOR : uniquement dans le périmètre du rôle. */
    public boolean deleteScoped(String role, int id) throws SQLException {
        String sql = "DELETE FROM notifications WHERE id = ? AND (target_role = ? OR target_role IS NULL)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, role);
            return ps.executeUpdate() > 0;
        }
    }

    /** Compteur léger non lu (polling). */
    public int countUnreadScoped(String role, Integer supplierId) throws SQLException {
        if ("SUPPLIER".equals(role) && supplierId != null) return countUnreadForSupplier(supplierId);
        return countUnread(role);
    }

    // ================= PRÉFÉRENCES =================

    /** Préférence effective (défaut activé ; SECURITY toujours activée). */
    public boolean isEnabled(String role, Integer userId, String category) throws SQLException {
        if ("SECURITY".equals(category)) return true;
        if (category == null) return true;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT enabled FROM notification_preferences WHERE owner_role = ? AND (owner_user_id <=> ?) AND category = ? LIMIT 1")) {
            ps.setString(1, role);
            if (userId != null) ps.setInt(2, userId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            return true; // table absente (pré-migration) = tout activé
        }
        // Repli : défaut du rôle (ligne owner_user_id NULL).
        if (userId != null) {
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT enabled FROM notification_preferences WHERE owner_role = ? AND owner_user_id IS NULL AND category = ? LIMIT 1")) {
                ps.setString(1, role);
                ps.setString(2, category);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean(1);
                }
            } catch (SQLException ignored) {}
        }
        return true;
    }

    public java.util.Map<String, Boolean> getPreferences(String role, Integer userId) throws SQLException {
        java.util.Map<String, Boolean> m = new java.util.LinkedHashMap<>();
        for (String c : new String[]{"ORDER", "PAYMENT", "DELIVERY", "MESSAGE", "NEGOTIATION", "PRODUCT", "SECURITY", "SYSTEM", "MARKETING"}) {
            m.put(c, isEnabled(role, userId, c));
        }
        return m;
    }

    public void setPreference(String role, Integer userId, String category, boolean enabled) throws SQLException {
        if ("SECURITY".equals(category)) return; // critique non désactivable
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO notification_preferences (owner_role, owner_user_id, category, enabled) VALUES (?, ?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)")) {
            ps.setString(1, role);
            if (userId != null) ps.setInt(2, userId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, category);
            ps.setBoolean(4, enabled);
            ps.executeUpdate();
        }
    }

    // ================= FOURNISSEUR PAGINÉ =================

    public List<Notification> findForSupplierPaged(int supplierId, String category, Boolean unreadOnly, int limit, int offset) throws SQLException {
        List<Notification> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM notifications WHERE target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL)");
        List<Object> params = new ArrayList<>();
        params.add(supplierId);
        if (category != null && !category.isEmpty()) {
            sql.append(" AND (`type` = ?").append(hasColumn("event_code") ? " OR category = ?" : "").append(")");
            params.add(category);
            if (hasColumn("event_code")) params.add(category);
        }
        if (unreadOnly != null) sql.append(unreadOnly ? " AND is_read = 0" : " AND is_read = 1");
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
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

    public int countForSupplierPaged(int supplierId, String category, Boolean unreadOnly) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM notifications WHERE target_role = 'SUPPLIER' AND (target_user_id = ? OR target_user_id IS NULL)");
        List<Object> params = new ArrayList<>();
        params.add(supplierId);
        if (category != null && !category.isEmpty()) {
            sql.append(" AND (`type` = ?").append(hasColumn("event_code") ? " OR category = ?" : "").append(")");
            params.add(category);
            if (hasColumn("event_code")) params.add(category);
        }
        if (unreadOnly != null) sql.append(unreadOnly ? " AND is_read = 0" : " AND is_read = 1");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
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
        // Colonnes v3 (tolérantes : absentes avant migration).
        try { n.setEventCode(rs.getString("event_code")); } catch (SQLException ignored) {}
        try { n.setCategory(rs.getString("category")); } catch (SQLException ignored) {}
        try { n.setEntityType(rs.getString("entity_type")); } catch (SQLException ignored) {}
        try { int eid = rs.getInt("entity_id"); n.setEntityId(rs.wasNull() ? null : eid); } catch (SQLException ignored) {}
        try { n.setActionUrl(rs.getString("action_url")); } catch (SQLException ignored) {}
        if (n.getActionUrl() == null) n.setActionUrl(n.getLink());
        try { n.setDedupKey(rs.getString("dedup_key")); } catch (SQLException ignored) {}
        try { n.setMetadata(rs.getString("metadata")); } catch (SQLException ignored) {}
        try { n.setReadAt(rs.getString("read_at")); } catch (SQLException ignored) {}
        return n;
    }
}
