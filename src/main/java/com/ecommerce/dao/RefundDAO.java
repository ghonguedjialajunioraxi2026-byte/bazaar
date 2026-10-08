package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Refund;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Remboursements PawaPay (table refunds).
 * Idempotence : refund_id UNIQUE + transitions conditionnelles
 * (seules les lignes non finales bougent).
 */
public class RefundDAO {

    public int create(Refund r) throws SQLException {
        String sql = "INSERT INTO refunds (order_id, payment_id, refund_id, deposit_id, amount, currency, status)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getOrderId());
            ps.setInt(2, r.getPaymentId());
            ps.setString(3, r.getRefundId());
            ps.setString(4, r.getDepositId());
            ps.setInt(5, r.getAmount());
            ps.setString(6, r.getCurrency() != null ? r.getCurrency() : "XAF");
            ps.setString(7, r.getStatus() != null ? r.getStatus() : "PENDING");
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public Refund findById(int id) throws SQLException {
        String sql = "SELECT * FROM refunds WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Refund findByRefundId(String refundId) throws SQLException {
        String sql = "SELECT * FROM refunds WHERE refund_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, refundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Dernier refund d'une commande (tous statuts). */
    public Refund findLatestByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM refunds WHERE order_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Refund non final d'une commande (anti double-remboursement). */
    public Refund findOpenByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM refunds WHERE order_id = ? AND status IN ('PENDING','PROCESSING')"
            + " ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Refund> findPaged(String status, int limit, int offset) throws SQLException {
        List<Refund> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM refunds");
        if (status != null && !status.isEmpty()) sql.append(" WHERE status = ?");
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null && !status.isEmpty()) ps.setString(i++, status);
            ps.setInt(i++, Math.max(1, Math.min(100, limit)));
            ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void updateAfterInitiation(int id, String status, String failureReason) throws SQLException {
        String sql = "UPDATE refunds SET status = ?, failure_reason = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, safe(failureReason));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Statuts locaux non finaux (un refund dans ces états peut encore
     * bouger : polling, callback, cancel). COMPLETED/FAILED sont terminaux
     * — aucun retour arrière.
     */
    public static final String OPEN_STATUSES =
        "'PENDING','PROCESSING','ACCEPTED','ENQUEUED'";

    /**
     * Transition conditionnelle vers un statut FINAL (callback/polling) :
     * n'avance que depuis un statut ouvert, JAMAIS depuis COMPLETED.
     * Retourne true si basculée.
     */
    public boolean transitionToFinal(int id, String toStatus, String failureReason) throws SQLException {
        return transitionToFinal(id, toStatus, failureReason, null);
    }

    public boolean transitionToFinal(int id, String toStatus, String failureReason,
            String providerTransactionId) throws SQLException {
        String sql = "UPDATE refunds SET status = ?, failure_reason = ?,"
            + " provider_transaction_id = COALESCE(?, provider_transaction_id),"
            + " last_reconciled_at = NOW()"
            + " WHERE id = ? AND status IN (" + OPEN_STATUSES + ")";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, safe(failureReason));
            ps.setString(3, providerTransactionId);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Avancée non finale (réconciliation) : PENDING → ACCEPTED/ENQUEUED/
     * PROCESSING, ACCEPTED → ENQUEUED/PROCESSING, ENQUEUED → ENQUEUED/
     * PROCESSING — jamais en arrière, jamais depuis un statut terminal.
     */
    public boolean transitionForward(int id, String toStatus, String providerTransactionId)
            throws SQLException {
        String sql = "UPDATE refunds SET status = ?,"
            + " provider_transaction_id = COALESCE(?, provider_transaction_id),"
            + " last_reconciled_at = NOW()"
            + " WHERE id = ? AND ("
            + " (status = 'PENDING' AND ? IN ('ACCEPTED','ENQUEUED','PROCESSING'))"
            + " OR (status = 'ACCEPTED' AND ? IN ('ENQUEUED','PROCESSING'))"
            + " OR (status = 'ENQUEUED' AND ? IN ('ENQUEUED','PROCESSING'))"
            + " OR (status = 'PROCESSING' AND ? IN ('PROCESSING'))"
            + ")";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, providerTransactionId);
            ps.setInt(3, id);
            ps.setString(4, toStatus);
            ps.setString(5, toStatus);
            ps.setString(6, toStatus);
            ps.setString(7, toStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Total déjà remboursé (COMPLETED) pour un dépôt — garde
     * refundAmount ≤ refundableAmount (full-only aujourd'hui, cumul
     * protégé pour tout partiel futur).
     */
    public int sumCompletedByDepositId(String depositId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM refunds"
            + " WHERE deposit_id = ? AND status = 'COMPLETED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, depositId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Touche la réconciliation sans changer le statut (polling sans mouvement). */
    public void touchReconciled(int id, String providerTransactionId) throws SQLException {
        String sql = "UPDATE refunds SET"
            + " provider_transaction_id = COALESCE(?, provider_transaction_id),"
            + " last_reconciled_at = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerTransactionId);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Comptage par statut (réconciliation admin). */
    public java.util.Map<String, Integer> countByStatus() throws SQLException {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        String sql = "SELECT status, COUNT(*) FROM refunds GROUP BY status";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? null : s.substring(0, Math.min(255, s.length()));
    }

    private Refund mapRow(ResultSet rs) throws SQLException {
        Refund r = new Refund();
        r.setId(rs.getInt("id"));
        r.setOrderId(rs.getInt("order_id"));
        r.setPaymentId(rs.getInt("payment_id"));
        r.setRefundId(rs.getString("refund_id"));
        r.setDepositId(rs.getString("deposit_id"));
        try { r.setProviderTransactionId(rs.getString("provider_transaction_id")); } catch (SQLException ignored) {}
        r.setAmount(rs.getInt("amount"));
        r.setCurrency(rs.getString("currency"));
        r.setStatus(rs.getString("status"));
        r.setFailureReason(rs.getString("failure_reason"));
        try { r.setLastReconciledAt(rs.getString("last_reconciled_at")); } catch (SQLException ignored) {}
        r.setCreatedAt(rs.getString("created_at"));
        try { r.setUpdatedAt(rs.getString("updated_at")); } catch (SQLException ignored) {}
        return r;
    }
}
