package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Payment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public int create(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, amount, currency, transaction_ref, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod() != null ? p.getMethod() : "WHATSAPP");
            ps.setInt(3, p.getAmount());
            ps.setString(4, p.getCurrency() != null ? p.getCurrency() : "FCFA");
            ps.setString(5, p.getTransactionRef());
            ps.setString(6, p.getStatus() != null ? p.getStatus() : "EN_ATTENTE");
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return 0;
        }
    }

    public Payment findById(int id) throws SQLException {
        String sql = "SELECT * FROM payments WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Payment findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Payment> findAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY created_at DESC LIMIT 500";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** Supervision admin : paiements + commande associée (1 requête, pas de N+1). */
    public List<java.util.Map<String, Object>> findPagedWithOrder(String status, int limit, int offset) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.*, o.order_number, o.fullname FROM payments p "
            + "LEFT JOIN orders o ON o.id = p.order_id");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            sql.append(" WHERE p.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, Math.min(100, limit)));
        params.add(Math.max(0, offset));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, String.valueOf(p));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("orderId", rs.getInt("order_id"));
                    m.put("orderNumber", rs.getString("order_number"));
                    m.put("customer", rs.getString("fullname"));
                    m.put("method", rs.getString("method"));
                    m.put("provider", rs.getString("provider"));
                    m.put("amount", rs.getInt("amount"));
                    m.put("currency", rs.getString("currency"));
                    m.put("status", rs.getString("status"));
                    m.put("transactionRef", rs.getString("transaction_ref"));
                    m.put("failureReason", rs.getString("failure_reason"));
                    m.put("paidAt", rs.getString("paid_at"));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM payments"
            + (status != null && !status.isEmpty() ? " WHERE status = ?" : "");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null && !status.isEmpty()) ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void updateStatus(int id, String status, String transactionRef) throws SQLException {
        String sql = "UPDATE payments SET status = ?, transaction_ref = ?, paid_at = IF(?='REUSSIE',NOW(),NULL) WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, transactionRef);
            ps.setString(3, status);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public int totalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status = 'REUSSIE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public List<Payment> findByStatus(String status) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE status = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getInt("id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setMethod(rs.getString("method"));
        p.setAmount(rs.getInt("amount"));
        p.setCurrency(rs.getString("currency"));
        p.setTransactionRef(rs.getString("transaction_ref"));
        p.setStatus(rs.getString("status"));
        try { p.setPaidAt(rs.getString("paid_at")); } catch (SQLException e) { p.setPaidAt(null); }
        p.setCreatedAt(rs.getString("created_at"));
        try { p.setProvider(rs.getString("provider")); } catch (SQLException ignored) {}
        try { p.setIdempotenceKey(rs.getString("idempotence_key")); } catch (SQLException ignored) {}
        try { p.setInternalReference(rs.getString("internal_reference")); } catch (SQLException ignored) {}
        try { p.setProviderTransactionId(rs.getString("provider_transaction_id")); } catch (SQLException ignored) {}
        try { p.setClientReferenceId(rs.getString("client_reference_id")); } catch (SQLException ignored) {}
        try { p.setOperator(rs.getString("operator")); } catch (SQLException ignored) {}
        try { p.setPhoneNumber(rs.getString("phone_number")); } catch (SQLException ignored) {}
        try { p.setUpdatedAt(rs.getString("updated_at")); } catch (SQLException ignored) {}
        try { p.setFailureReason(rs.getString("failure_reason")); } catch (SQLException ignored) {}
        try {
            int vb = rs.getInt("verified_by");
            p.setVerifiedBy(rs.wasNull() ? null : vb);
        } catch (SQLException ignored) {}
        return p;
    }

    public Payment findByIdempotenceKey(String key) throws SQLException {
        String sql = "SELECT * FROM payments WHERE idempotence_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public int createFull(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, provider, amount, currency, transaction_ref, idempotence_key, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod());
            ps.setString(3, p.getProvider());
            ps.setInt(4, p.getAmount());
            ps.setString(5, p.getCurrency() != null ? p.getCurrency() : "FCFA");
            ps.setString(6, p.getTransactionRef());
            ps.setString(7, p.getIdempotenceKey());
            ps.setString(8, p.getStatus() != null ? p.getStatus() : "EN_ATTENTE");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    public void updateReference(int id, String transactionRef) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET transaction_ref = ? WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setString(1, transactionRef);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markVerified(int id, int adminId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'REUSSIE', verified_by = ?, verified_at = NOW(), paid_at = NOW(), failure_reason = NULL WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setInt(1, adminId);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markFailed(int id, String reason) throws SQLException {
        String safe = reason == null ? null : reason.substring(0, Math.min(255, reason.length()));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'ECHOUEE', failure_reason = ? WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setString(1, safe);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markCancelled(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE payments SET status = 'ANNULEE' WHERE id = ? AND status = 'EN_ATTENTE'")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // PawaPay (colonnes migrate_pawapay.sql ; écritures conditionnelles
    // pour une idempotence sûre sous retries/webhooks répétés).
    // ------------------------------------------------------------------

    /** Ligne PawaPay créée AVANT l'appel réseau (réconciliation garantie). */
    public int createPawaPay(Payment p) throws SQLException {
        String sql = "INSERT INTO payments (order_id, method, provider, amount, currency,"
            + " transaction_ref, idempotence_key, internal_reference, provider_transaction_id,"
            + " client_reference_id, operator, phone_number, status)"
            + " VALUES (?, ?, 'PAWAPAY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod());
            ps.setInt(3, p.getAmount());
            ps.setString(4, p.getCurrency() != null ? p.getCurrency() : "XAF");
            ps.setString(5, p.getTransactionRef());
            ps.setString(6, p.getIdempotenceKey());
            ps.setString(7, p.getInternalReference());
            ps.setString(8, p.getProviderTransactionId());
            ps.setString(9, p.getClientReferenceId());
            ps.setString(10, p.getOperator());
            ps.setString(11, p.getPhoneNumber());
            ps.setString(12, p.getStatus() != null ? p.getStatus() : "PENDING");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    /** Dernière tentative PawaPay d'une commande, quel que soit son statut. */
    public Payment findLatestPawaPayByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? AND provider = 'PAWAPAY'"
            + " ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Payment findByInternalReference(String ref) throws SQLException {
        String sql = "SELECT * FROM payments WHERE internal_reference = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Payment findByProviderTransactionId(String depositId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE provider_transaction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, depositId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Dernière tentative PawaPay non finale d'une commande (anti-doublon). */
    public Payment findOpenPawaPayByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? AND provider = 'PAWAPAY'"
            + " AND status IN ('PENDING','PROCESSING','EN_ATTENTE')"
            + " ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** MAJ après initiation PawaPay (ACCEPTED → PROCESSING, etc.). */
    public void updateAfterInitiation(int id, String status, String failureReason) throws SQLException {
        String safe = failureReason == null ? null
            : failureReason.substring(0, Math.min(255, failureReason.length()));
        String sql = "UPDATE payments SET status = ?, failure_reason = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, safe);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Transition webhook conditionnelle : ne touche que les lignes non
     * finales. Retourne true si la ligne a basculé (false = doublon ou
     * déjà finale → ne rien recompter).
     */
    public boolean transitionFromWebhook(int id, String toStatus, String failureReason) throws SQLException {
        String safe = failureReason == null ? null
            : failureReason.substring(0, Math.min(255, failureReason.length()));
        String sql = "UPDATE payments SET status = ?, failure_reason = ?,"
            + " paid_at = IF(? IN ('COMPLETED','REUSSIE'), NOW(), paid_at)"
            + " WHERE id = ? AND status IN ('PENDING','PROCESSING','EN_ATTENTE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, safe);
            ps.setString(3, toStatus);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Rouvre une tentative après échec (nouvelle référence à fournir). */
    public void reopen(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
              PreparedStatement ps = conn.prepareStatement(
                  "UPDATE payments SET status = 'EN_ATTENTE' WHERE id = ? AND status = 'ECHOUEE'")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Confirmation MODE TEST (sandbox uniquement, jamais en production).
     * Simule un dépôt réussi sans appel PawaPay : la référence est
     * préfixée TEST- et traçable. Retourne false si déjà traité.
     */
    public boolean markTestConfirmed(int id, String ref) throws SQLException {
        String safe = ref == null ? "TEST" : ref.substring(0, Math.min(120, ref.length()));
        try (Connection conn = DBConnection.getConnection();
              PreparedStatement ps = conn.prepareStatement(
                  "UPDATE payments SET status = 'REUSSIE', transaction_ref = ?,"
                  + " paid_at = NOW(), failure_reason = NULL"
                  + " WHERE id = ? AND status IN ('EN_ATTENTE','ECHOUEE')")) {
            ps.setString(1, safe);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Dépôt PawaPay soldé (COMPLETED) d'une commande, pour payout/refund. */
    public Payment findCompletedPawaPayByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ? AND provider = 'PAWAPAY'"
            + " AND status IN ('COMPLETED','REUSSIE')"
            + " ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Bascule un dépôt COMPLETED → REFUNDED (remboursement PawaPay confirmé).
     * Conditionnelle : un doublon de callback ne re-déclenche rien.
     */
    public boolean markRefundedIfCompleted(int id) throws SQLException {
        String sql = "UPDATE payments SET status = 'REFUNDED'"
            + " WHERE id = ? AND status IN ('COMPLETED','REUSSIE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
