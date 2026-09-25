package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Conversation;
import com.ecommerce.model.SupplierMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ConversationDAO {

    public List<Conversation> findBySupplier(int supplierId) throws SQLException {
        List<Conversation> list = new ArrayList<>();
        String sql = "SELECT c.*, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender='CLIENT' AND m.is_read=0) AS unread_count, "
                + "(SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message, "
                + "(SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message_at "
                + "FROM conversations c WHERE c.supplier_id = ? ORDER BY c.updated_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, true));
            }
        }
        return list;
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

    public int create(Conversation c) throws SQLException {
        String sql = "INSERT INTO conversations (supplier_id, client_identifier, client_name, client_phone, order_id, product_id, subject, status) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getSupplierId());
            ps.setString(2, c.getClientIdentifier());
            ps.setString(3, c.getClientName());
            ps.setString(4, c.getClientPhone());
            if (c.getOrderId() != null) ps.setInt(5, c.getOrderId()); else ps.setNull(5, Types.INTEGER);
            if (c.getProductId() != null) ps.setInt(6, c.getProductId()); else ps.setNull(6, Types.INTEGER);
            ps.setString(7, c.getSubject());
            ps.setString(8, c.getStatus() == null ? "OPEN" : c.getStatus());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
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
                + "SET m.is_read=1 WHERE c.id = ? AND c.supplier_id = ? AND m.sender='CLIENT'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.setInt(2, supplierId);
            ps.executeUpdate();
        }
    }

    public List<SupplierMessage> findMessages(int conversationId) throws SQLException {
        List<SupplierMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SupplierMessage m = new SupplierMessage();
                    m.setId(rs.getInt("id"));
                    m.setConversationId(rs.getInt("conversation_id"));
                    m.setSender(rs.getString("sender"));
                    m.setBody(rs.getString("body"));
                    m.setRead(rs.getBoolean("is_read"));
                    m.setCreatedAt(rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
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

    private Conversation mapRow(ResultSet rs, boolean withSummary) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getInt("id"));
        c.setSupplierId(rs.getInt("supplier_id"));
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
