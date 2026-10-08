package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Conversation;
import com.ecommerce.model.SupplierMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ConversationDAO {

    public List<Conversation> findBySupplier(int supplierId) throws SQLException {
        return findBySupplier(supplierId, 200);
    }

    public List<Conversation> findBySupplier(int supplierId, int cap) throws SQLException {
        List<Conversation> list = new ArrayList<>();
        String sql = "SELECT c.*, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender='CLIENT' AND m.is_read=0) AS unread_count, "
                + "(SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message, "
                + "(SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message_at "
                + "FROM conversations c WHERE c.supplier_id = ? ORDER BY c.updated_at DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.setInt(2, Math.max(1, Math.min(500, cap)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, true));
            }
        }
        return list;
    }

    /**
     * Supervision admin (lecture seule) : conversations + boutique + non lus.
     * Ne retourne jamais le contenu des messages (confidentialité).
     */
    public List<java.util.Map<String, Object>> findAllForAdmin(int limit, int offset) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT c.id, c.supplier_id, s.name AS supplier_name, c.client_name, "
                + "c.client_phone, c.subject, c.status, c.updated_at, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender='CLIENT' AND m.is_read=0) AS unread_count, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS message_count, "
                + "(SELECT COUNT(*) FROM offers o WHERE o.conversation_id = c.id AND o.status='PENDING') AS pending_offers "
                + "FROM conversations c LEFT JOIN suppliers s ON s.id = c.supplier_id "
                + "ORDER BY c.updated_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("supplierId", rs.getInt("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("clientName", rs.getString("client_name"));
                    m.put("clientPhone", maskPhone(rs.getString("client_phone")));
                    m.put("subject", rs.getString("subject"));
                    m.put("status", rs.getString("status"));
                    m.put("unreadCount", rs.getInt("unread_count"));
                    m.put("messageCount", rs.getInt("message_count"));
                    m.put("pendingOffers", rs.getInt("pending_offers"));
                    m.put("updatedAt", rs.getString("updated_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM conversations");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /** Total messages clients non lus (KPI supervision). */
    public int unreadTotal() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id "
                 + "WHERE m.sender='CLIENT' AND m.is_read=0");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /** Masquage téléphone côté supervision (confidentialité : 6XX XX 12). */
    static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) return "***";
        return "*** ** " + digits.substring(digits.length() - 2);
    }

    public Conversation findByIdScoped(int supplierId, int id) throws SQLException {
        String sql = "SELECT c.* FROM conversations c WHERE c.id = ? AND c.supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs, false);
            }
        }
        return null;
    }

    /** Lecture serveur non scopée (usage interne : notifications). Jamais exposée telle quelle. */
    public Conversation findById(int id) throws SQLException {
        String sql = "SELECT c.* FROM conversations c WHERE c.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs, false);
            }
        }
        return null;
    }

    public int create(Conversation c) throws SQLException {
        String sql = "INSERT INTO conversations (supplier_id, customer_id, client_identifier, client_name, client_phone, order_id, product_id, subject, status) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getSupplierId());
            if (c.getCustomerId() > 0) ps.setInt(2, c.getCustomerId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, c.getClientIdentifier());
            ps.setString(4, c.getClientName());
            ps.setString(5, c.getClientPhone());
            if (c.getOrderId() != null) ps.setInt(6, c.getOrderId()); else ps.setNull(6, Types.INTEGER);
            if (c.getProductId() != null) ps.setInt(7, c.getProductId()); else ps.setNull(7, Types.INTEGER);
            ps.setString(8, c.getSubject());
            ps.setString(9, c.getStatus() == null ? "OPEN" : c.getStatus());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    /** Lie une conversation invitée au compte client (migration douce, 1ère utilisation). */
    public void linkCustomer(int conversationId, int customerId) throws SQLException {
        String sql = "UPDATE conversations SET customer_id = ? WHERE id = ? AND (customer_id IS NULL OR customer_id = 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, conversationId);
            ps.executeUpdate();
        }
    }

    /** Conversations d'un client authentifié (compte prioritaire sur phone+cid). */
    public List<Conversation> findByCustomer(int customerId) throws SQLException {
        List<Conversation> list = new ArrayList<>();
        String sql = "SELECT c.*, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender='SUPPLIER' AND m.is_read=0) AS unread_count, "
                + "(SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message, "
                + "(SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message_at "
                + "FROM conversations c WHERE c.customer_id = ? ORDER BY c.updated_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, true));
            }
        }
        return list;
    }

    /** Conversation strictement limitée à son propriétaire authentifié (anti-IDOR). */
    public Conversation findByIdCustomer(int id, int customerId) throws SQLException {
        String sql = "SELECT * FROM conversations WHERE id = ? AND customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs, false);
            }
        }
        return null;
    }

    public int unreadCountForCustomer(int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages m "
                + "JOIN conversations c ON c.id = m.conversation_id "
                + "WHERE c.customer_id = ? AND m.sender='SUPPLIER' AND m.is_read=0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Fiche produit pour contexte conversation (nom, prix, image primaire).
     * Jamais de données vendeur sensibles.
     */
    public java.util.Map<String, Object> productSnippet(int productId) throws SQLException {
        String sql = "SELECT p.id, p.name, p.price, "
                + "(SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id "
                + "ORDER BY pi.is_primary DESC, pi.sort_order ASC LIMIT 1) AS image "
                + "FROM products p WHERE p.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("name", rs.getString("name"));
                    m.put("price", rs.getInt("price"));
                    m.put("image", rs.getString("image"));
                    return m;
                }
            }
        }
        return null;
    }

    /** Numéro de commande pour contexte (lecture seule). */
    public String findOrderNumber(Integer orderId) throws SQLException {
        if (orderId == null) return null;
        String sql = "SELECT order_number FROM orders WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public int unreadCountForSupplier(int supplierId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages m "
                + "JOIN conversations c ON c.id = m.conversation_id "
                + "WHERE c.supplier_id = ? AND m.sender='CLIENT' AND m.is_read=0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void markConversationRead(int supplierId, int conversationId) throws SQLException {
        String sql = "UPDATE messages m JOIN conversations c ON c.id = m.conversation_id "
                + "SET m.is_read=1, m.read_at = IF(m.read_at IS NULL, NOW(), m.read_at) WHERE c.id = ? AND c.supplier_id = ? AND m.sender='CLIENT'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.setInt(2, supplierId);
            ps.executeUpdate();
        }
    }

    public List<SupplierMessage> findMessages(int conversationId) throws SQLException {
        return findMessagesAfter(conversationId, 0);
    }

    /**
     * Récupération incrémentale (polling) : seuls les messages après
     * {@code afterId}, bornés à 200. Sans param = 300 derniers (Phase 17).
     */
    public List<SupplierMessage> findMessagesAfter(int conversationId, int afterId) throws SQLException {
        List<SupplierMessage> list = new ArrayList<>();
        String sql = afterId > 0
            ? "SELECT * FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id ASC LIMIT 200"
            : "SELECT * FROM (SELECT * FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT 300) t ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            if (afterId > 0) ps.setInt(2, afterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMessage(rs));
            }
        }
        return list;
    }

    private SupplierMessage mapMessage(ResultSet rs) throws SQLException {
        SupplierMessage m = new SupplierMessage();
        m.setId(rs.getInt("id"));
        m.setConversationId(rs.getInt("conversation_id"));
        m.setSender(rs.getString("sender"));
        m.setBody(rs.getString("body"));
        m.setRead(rs.getBoolean("is_read"));
        m.setCreatedAt(rs.getString("created_at"));
        return m;
    }

    public int addMessage(int conversationId, String sender, String body) throws SQLException {
        String sql = "INSERT INTO messages (conversation_id, sender, body) VALUES (?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, conversationId);
            ps.setString(2, sender);
            ps.setString(3, body);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public void updateActivity(int conversationId) throws SQLException {
        String sql = "UPDATE conversations SET updated_at = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.executeUpdate();
        }
    }

    /** Conversations d'un invité (téléphone + clé navigateur), avec résumé. */
    public List<Conversation> findByGuest(String phone, String cid) throws SQLException {
        List<Conversation> list = new ArrayList<>();
        String sql = "SELECT c.*, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender='SUPPLIER' AND m.is_read=0) AS unread_count, "
                + "(SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message, "
                + "(SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message_at "
                + "FROM conversations c WHERE c.client_phone = ? AND c.client_identifier = ? ORDER BY c.updated_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, cid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, true));
            }
        }
        return list;
    }

    /** Conversation strictement limitée à son propriétaire invité (anti-IDOR). */
    public Conversation findByIdGuest(int id, String phone, String cid) throws SQLException {
        String sql = "SELECT * FROM conversations WHERE id = ? AND client_phone = ? AND client_identifier = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, phone);
            ps.setString(3, cid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs, false);
            }
        }
        return null;
    }

    public int unreadCountForGuest(String phone, String cid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages m "
                + "JOIN conversations c ON c.id = m.conversation_id "
                + "WHERE c.client_phone = ? AND c.client_identifier = ? AND m.sender='SUPPLIER' AND m.is_read=0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, cid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void markGuestRead(int conversationId) throws SQLException {
        String sql = "UPDATE messages SET is_read=1, read_at = IF(read_at IS NULL, NOW(), read_at) WHERE conversation_id = ? AND sender='SUPPLIER'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.executeUpdate();
        }
    }

    /** Téléphone propriétaire d'une conversation (vérification offre). */
    public String findClientPhone(int conversationId) throws SQLException {
        String sql = "SELECT client_phone FROM conversations WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    /** Nombre de messages d'un expéditeur depuis N minutes (anti-spam). */
    public int countRecentFrom(int conversationId, String sender, int minutes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND sender = ? AND created_at >= NOW() - INTERVAL ? MINUTE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.setString(2, sender);
            ps.setInt(3, minutes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private Conversation mapRow(ResultSet rs, boolean withSummary) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getInt("id"));
        c.setSupplierId(rs.getInt("supplier_id"));
        try {
            int cuid = rs.getInt("customer_id");
            c.setCustomerId(rs.wasNull() ? 0 : cuid);
        } catch (SQLException ignored) {}
        c.setClientIdentifier(rs.getString("client_identifier"));
        c.setClientName(rs.getString("client_name"));
        c.setClientPhone(rs.getString("client_phone"));
        int oid = rs.getInt("order_id");
        c.setOrderId(rs.wasNull() ? null : oid);
        int pid = rs.getInt("product_id");
        c.setProductId(rs.wasNull() ? null : pid);
        c.setSubject(rs.getString("subject"));
        c.setStatus(rs.getString("status"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        if (withSummary) {
            try {
                c.setUnreadCount(rs.getInt("unread_count"));
                c.setLastMessage(rs.getString("last_message"));
                c.setLastMessageAt(rs.getString("last_message_at"));
            } catch (SQLException ignored) {}
        }
        return c;
    }
}
