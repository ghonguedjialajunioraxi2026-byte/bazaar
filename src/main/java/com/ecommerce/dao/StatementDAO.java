package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.PawaPayStatement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Relevés PawaPay (table pawapay_statements).
 * Transitions conditionnelles : ACCEPTED/PROCESSING → PROCESSING/
 * COMPLETED/FAILED. COMPLETED/FAILED terminaux (jamais de retour).
 */
public class StatementDAO {

    public static final String OPEN_STATUSES = "'ACCEPTED','PROCESSING'";

    public int create(PawaPayStatement s) throws SQLException {
        String sql = "INSERT INTO pawapay_statements (statement_id, status,"
            + " wallet_country, wallet_currency, wallet_provider,"
            + " start_date, end_date, callback_url, requested_by)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getStatementId());
            ps.setString(2, s.getStatus() != null ? s.getStatus() : "ACCEPTED");
            ps.setString(3, s.getWalletCountry());
            ps.setString(4, s.getWalletCurrency());
            if (s.getWalletProvider() != null) ps.setString(5, s.getWalletProvider());
            else ps.setNull(5, Types.VARCHAR);
            ps.setString(6, s.getStartDate());
            ps.setString(7, s.getEndDate());
            ps.setString(8, s.getCallbackUrl());
            if (s.getRequestedBy() != null) ps.setInt(9, s.getRequestedBy());
            else ps.setNull(9, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public PawaPayStatement findByStatementId(String statementId) throws SQLException {
        String sql = "SELECT * FROM pawapay_statements WHERE statement_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statementId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public PawaPayStatement findById(int id) throws SQLException {
        String sql = "SELECT * FROM pawapay_statements WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<PawaPayStatement> findRecent(int limit) throws SQLException {
        List<PawaPayStatement> list = new ArrayList<>();
        String sql = "SELECT * FROM pawapay_statements ORDER BY id DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int[] countByStatus() throws SQLException {
        // [ACCEPTED/PROCESSING ouverts, COMPLETED, FAILED]
        int[] out = new int[3];
        String sql = "SELECT status, COUNT(*) FROM pawapay_statements GROUP BY status";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String st = rs.getString(1);
                int n = rs.getInt(2);
                if ("COMPLETED".equals(st)) out[1] += n;
                else if ("FAILED".equals(st)) out[2] += n;
                else out[0] += n;
            }
        }
        return out;
    }

    /**
     * Transition conditionnelle : ACCEPTED/PROCESSING → PROCESSING/
     * COMPLETED/FAILED uniquement. Retourne true si basculée.
     * Persiste downloadUrl/expiry/completedAt/failedAt quand fournis.
     */
    public boolean transition(int id, String toStatus, String failureReason,
            String downloadUrl, String downloadUrlExpiresAt,
            String completedAt, String failedAt) throws SQLException {
        String sql = "UPDATE pawapay_statements SET status = ?, failure_reason = ?,"
            + " download_url = COALESCE(?, download_url),"
            + " download_url_expires_at = COALESCE(?, download_url_expires_at),"
            + " completed_at = COALESCE(?, completed_at),"
            + " failed_at = COALESCE(?, failed_at),"
            + " last_reconciled_at = NOW()"
            + " WHERE id = ? AND status IN (" + OPEN_STATUSES + ")"
            + " AND ? IN ('PROCESSING','COMPLETED','FAILED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatus);
            ps.setString(2, safe(failureReason));
            ps.setString(3, downloadUrl);
            ps.setString(4, downloadUrlExpiresAt);
            ps.setString(5, completedAt);
            ps.setString(6, failedAt);
            ps.setInt(7, id);
            ps.setString(8, toStatus);
            return ps.executeUpdate() > 0;
        }
    }

    /** Touche la réconciliation sans changer le statut. */
    public void touchReconciled(int id) throws SQLException {
        String sql = "UPDATE pawapay_statements SET last_reconciled_at = NOW() WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static String safe(String s) {
        return s == null ? null : s.substring(0, Math.min(255, s.length()));
    }

    private PawaPayStatement mapRow(ResultSet rs) throws SQLException {
        PawaPayStatement s = new PawaPayStatement();
        s.setId(rs.getInt("id"));
        s.setStatementId(rs.getString("statement_id"));
        s.setStatus(rs.getString("status"));
        s.setWalletCountry(rs.getString("wallet_country"));
        s.setWalletCurrency(rs.getString("wallet_currency"));
        try { s.setWalletProvider(rs.getString("wallet_provider")); } catch (SQLException ignored) {}
        s.setStartDate(rs.getString("start_date"));
        s.setEndDate(rs.getString("end_date"));
        s.setCallbackUrl(rs.getString("callback_url"));
        try { s.setDownloadUrl(rs.getString("download_url")); } catch (SQLException ignored) {}
        try { s.setDownloadUrlExpiresAt(rs.getString("download_url_expires_at")); } catch (SQLException ignored) {}
        try { s.setCompletedAt(rs.getString("completed_at")); } catch (SQLException ignored) {}
        try { s.setFailedAt(rs.getString("failed_at")); } catch (SQLException ignored) {}
        s.setFailureReason(rs.getString("failure_reason"));
        try {
            int rb = rs.getInt("requested_by");
            s.setRequestedBy(rs.wasNull() ? null : rb);
        } catch (SQLException ignored) {}
        s.setCreatedAt(rs.getString("created_at"));
        try { s.setUpdatedAt(rs.getString("updated_at")); } catch (SQLException ignored) {}
        try { s.setLastReconciledAt(rs.getString("last_reconciled_at")); } catch (SQLException ignored) {}
        return s;
    }
}
