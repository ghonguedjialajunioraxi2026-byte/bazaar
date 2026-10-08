package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.SupplierPayment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Versements PawaPay aux fournisseurs (lignes "auto" de supplier_payments :
 * 1 commande = au plus 1 payout, order_id UNIQUE).
 * Les retraits manuels historiques (order_id NULL) sont inchangés.
 */
public class PayoutDAO {

    /** Crée une ligne de payout AVANT l'appel PawaPay (réconciliation garantie). */
    public int createAuto(SupplierPayment sp) throws SQLException {
        String sql = "INSERT INTO supplier_payments (supplier_id, order_id, reference, amount,"
            + " method, status, notes, payout_id, provider, phone_number,"
            + " gross_amount, commission, pawapay_fee)"
            + " VALUES (?, ?, ?, ?, 'PAWAPAY', ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sp.getSupplierId());
            if (sp.getOrderId() != null) ps.setInt(2, sp.getOrderId());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, sp.getReference());
            ps.setInt(4, sp.getAmount());
            ps.setString(5, sp.getStatus() != null ? sp.getStatus() : "PENDING");
            ps.setString(6, sp.getNotes());
            ps.setString(7, sp.getPayoutId());
            ps.setString(8, sp.getProvider());
            ps.setString(9, sp.getPhoneNumber());
            if (sp.getGrossAmount() != null) ps.setInt(10, sp.getGrossAmount());
            else ps.setNull(10, Types.INTEGER);
            if (sp.getCommission() != null) ps.setInt(11, sp.getCommission());
            else ps.setNull(11, Types.INTEGER);
            if (sp.getPawapayFee() != null) ps.setInt(12, sp.getPawapayFee());
            else ps.setNull(12, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public SupplierPayment findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM supplier_payments WHERE order_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public SupplierPayment findByPayoutId(String payoutId) throws SQLException {
        String sql = "SELECT * FROM supplier_payments WHERE payout_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payoutId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public SupplierPayment findById(int id) throws SQLException {
        String sql = "SELECT * FROM supplier_payments WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Payouts automatiques d'un fournisseur (suivi vendeur). */
    public List<SupplierPayment> findAutoBySupplier(int supplierId) throws SQLException {
        List<SupplierPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM supplier_payments WHERE supplier_id = ? AND order_id IS NOT NULL"
            + " ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Supervision admin (payouts auto + retraits manuels). */
    public List<java.util.Map<String, Object>> findPagedWithOrder(String status, int limit, int offset)
            throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT sp.*, o.order_number, s.name AS supplier_name FROM supplier_payments sp"
            + " LEFT JOIN orders o ON o.id = sp.order_id"
            + " LEFT JOIN suppliers s ON s.id = sp.supplier_id");
        if (status != null && !status.isEmpty()) sql.append(" WHERE sp.status = ?");
        sql.append(" ORDER BY sp.id DESC LIMIT ? OFFSET ?");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null && !status.isEmpty()) ps.setString(i++, status);
            ps.setInt(i++, Math.max(1, Math.min(100, limit)));
            ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("supplierId", rs.getInt("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("orderId", rs.getObject("order_id"));
                    m.put("orderNumber", rs.getString("order_number"));
                    m.put("reference", rs.getString("reference"));
                    m.put("amount", rs.getInt("amount"));
                    try { m.put("grossAmount", rs.getObject("gross_amount")); } catch (SQLException ignored) {}
                    try { m.put("commission", rs.getObject("commission")); } catch (SQLException ignored) {}
                    try { m.put("pawapayFee", rs.getObject("pawapay_fee")); } catch (SQLException ignored) {}
                    m.put("method", rs.getString("method"));
                    m.put("status", rs.getString("status"));
                    try { m.put("payoutId", rs.getString("payout_id")); } catch (SQLException ignored) {}
                    try { m.put("provider", rs.getString("provider")); } catch (SQLException ignored) {}
                    try { m.put("providerTransactionId", rs.getString("provider_transaction_id")); } catch (SQLException ignored) {}
                    try { m.put("failureReason", rs.getString("failure_reason")); } catch (SQLException ignored) {}
                    try { m.put("lastReconciledAt", rs.getString("last_reconciled_at")); } catch (SQLException ignored) {}
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public void updateAfterInitiation(int id, String status, String failureReason) throws SQLException {
        String sql = "UPDATE supplier_payments SET status = ?, failure_reason = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, safe(failureReason));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Statuts locaux non finaux (un payout dans ces états peut encore
     * bouger : polling, callback, cancel). Tout le reste (COMPLETED, PAID,
     * FAILED, CANCELLED, BLOCKED) est terminal côté BAZAAR — sauf preuve
     * contraire du serveur (§ transitionToFinal élargie, avec garde).
     */
    public static final String OPEN_STATUSES =
        "'PENDING','PROCESSING','ACCEPTED','ENQUEUED'";

    /**
     * Transition conditionnelle vers un statut FINAL (callback/polling) :
     * n'avance que depuis un statut ouvert, JAMAIS depuis COMPLETED/PAID
     * (aucun retour arrière financier). Exception explicite : un statut
     * final distant (COMPLETED/FAILED prouvé par GET) écrase CANCELLED/
     * BLOCKED local — la vérité mouvement-de-fonds prime, avec warning
     * obligatoire côté service. Retourne true si basculée.
     */
    public boolean transitionToFinal(int id, String toStatus, String failureReason) throws SQLException {
        return transitionToFinal(id, toStatus, failureReason, null);
    }

    public boolean transitionToFinal(int id, String toStatus, String failureReason,
            String providerTransactionId) throws SQLException {
        String sql = "UPDATE supplier_payments SET status = ?, failure_reason = ?,"
            + " provider_transaction_id = COALESCE(?, provider_transaction_id),"
            + " last_reconciled_at = NOW()"
            + " WHERE id = ? AND (status IN (" + OPEN_STATUSES + ")"
            + " OR (status IN ('CANCELLED','BLOCKED')"
            + " AND ? IN ('COMPLETED','FAILED')))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, safe(failureReason));
            ps.setString(3, providerTransactionId);
            ps.setInt(4, id);
            ps.setString(5, toStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Avancée non finale (réconciliation) : PENDING → ACCEPTED/ENQUEUED/
     * PROCESSING, ACCEPTED/ENQUEUED → PROCESSING/ENQUEUED — jamais en
     * arrière, jamais depuis un statut terminal. Persiste aussi la
     * référence provider et l'horodatage de réconciliation.
     */
    public boolean transitionForward(int id, String toStatus, String providerTransactionId)
            throws SQLException {
        String sql = "UPDATE supplier_payments SET status = ?,"
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

    /** Comptage par statut (réconciliation admin). */
    public java.util.Map<String, Integer> countByStatus() throws SQLException {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        String sql = "SELECT status, COUNT(*) FROM supplier_payments"
            + " WHERE method = 'PAWAPAY' GROUP BY status";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
        }
        return out;
    }

    /** Touche la réconciliation sans changer le statut (polling sans mouvement). */
    public void touchReconciled(int id, String providerTransactionId) throws SQLException {        String sql = "UPDATE supplier_payments SET"
            + " provider_transaction_id = COALESCE(?, provider_transaction_id),"
            + " last_reconciled_at = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerTransactionId);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Annule un payout ouvert (litige, remboursement) : jamais depuis un statut final. */
    public boolean cancelIfOpen(int id, String reason) throws SQLException {
        String sql = "UPDATE supplier_payments SET status = 'CANCELLED', failure_reason = ?"
            + " WHERE id = ? AND status IN (" + OPEN_STATUSES + ",'BLOCKED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, safe(reason));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static String safe(String s) {
        return s == null ? null : s.substring(0, Math.min(255, s.length()));
    }

    public static SupplierPayment mapRow(ResultSet rs) throws SQLException {
        SupplierPayment sp = new SupplierPayment();
        sp.setId(rs.getInt("id"));
        sp.setSupplierId(rs.getInt("supplier_id"));
        try {
            int oid = rs.getInt("order_id");
            sp.setOrderId(rs.wasNull() ? null : oid);
        } catch (SQLException ignored) {}
        try { sp.setPayoutId(rs.getString("payout_id")); } catch (SQLException ignored) {}
        try { sp.setProviderTransactionId(rs.getString("provider_transaction_id")); } catch (SQLException ignored) {}
        try { sp.setProvider(rs.getString("provider")); } catch (SQLException ignored) {}
        try { sp.setPhoneNumber(rs.getString("phone_number")); } catch (SQLException ignored) {}
        try {
            int g = rs.getInt("gross_amount");
            sp.setGrossAmount(rs.wasNull() ? null : g);
        } catch (SQLException ignored) {}
        try {
            int c = rs.getInt("commission");
            sp.setCommission(rs.wasNull() ? null : c);
        } catch (SQLException ignored) {}
        try {
            int f = rs.getInt("pawapay_fee");
            sp.setPawapayFee(rs.wasNull() ? null : f);
        } catch (SQLException ignored) {}
        try { sp.setFailureReason(rs.getString("failure_reason")); } catch (SQLException ignored) {}
        try { sp.setLastReconciledAt(rs.getString("last_reconciled_at")); } catch (SQLException ignored) {}
        sp.setReference(rs.getString("reference"));
        sp.setAmount(rs.getInt("amount"));
        sp.setMethod(rs.getString("method"));
        sp.setStatus(rs.getString("status"));
        sp.setNotes(rs.getString("notes"));
        sp.setCreatedAt(rs.getString("created_at"));
        sp.setUpdatedAt(rs.getString("updated_at"));
        return sp;
    }
}
