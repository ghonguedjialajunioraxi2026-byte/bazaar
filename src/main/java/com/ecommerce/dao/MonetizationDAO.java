package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.LedgerEntry;
import com.ecommerce.model.MonetizationRule;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Monétisation (Phase 10). Tout en FCFA entiers, taux en points de base,
 * arrondi HALF_UP : commission = (net * bps + 5000) / 10000.
 */
public class MonetizationDAO {

    /** Règle applicable maintenant : active + fenêtre de dates, sinon défaut 10 % / 0. */
    public MonetizationRule resolveRule(Connection conn) throws SQLException {
        String sql = "SELECT * FROM monetization_rules WHERE is_active = 1 "
            + "AND (starts_at IS NULL OR starts_at <= NOW()) "
            + "AND (ends_at IS NULL OR ends_at >= NOW()) "
            + "ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return mapRule(rs);
        }
        MonetizationRule d = new MonetizationRule();
        d.setId(0);
        d.setName("Défaut 1 %");
        d.setCommissionBps(100);
        d.setFixedFee(0);
        d.setActive(true);
        return d;
    }

    public List<MonetizationRule> findAllRules() throws SQLException {
        List<MonetizationRule> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM monetization_rules ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRule(rs));
        }
        return list;
    }

    public int createRule(MonetizationRule r) throws SQLException {
        String sql = "INSERT INTO monetization_rules (name, commission_bps, fixed_fee, is_active, starts_at, ends_at) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getName());
            ps.setInt(2, r.getCommissionBps());
            ps.setInt(3, r.getFixedFee());
            ps.setBoolean(4, r.isActive());
            ps.setString(5, null);
            ps.setString(6, null);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) return k.getInt(1);
            }
            return 0;
        }
    }

    public void setRuleActive(int id, boolean active) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE monetization_rules SET is_active = ? WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public boolean hasEntries(int orderId, String kind) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM financial_ledger WHERE order_id = ? AND kind = ?")) {
            ps.setInt(1, orderId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void insert(Connection conn, LedgerEntry e) throws SQLException {
        String sql = "INSERT INTO financial_ledger (reference, order_id, supplier_id, kind, gross, discount, net, commission_bps, commission, fixed_fee, supplier_earnings, status) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getReference());
            ps.setInt(2, e.getOrderId());
            if (e.getSupplierId() != null) ps.setInt(3, e.getSupplierId());
            else ps.setNull(3, Types.INTEGER);
            ps.setString(4, e.getKind());
            ps.setInt(5, e.getGross());
            ps.setInt(6, e.getDiscount());
            ps.setInt(7, e.getNet());
            ps.setInt(8, e.getCommissionBps());
            ps.setInt(9, e.getCommission());
            ps.setInt(10, e.getFixedFee());
            ps.setInt(11, e.getSupplierEarnings());
            ps.setString(12, e.getStatus());
            ps.executeUpdate();
        }
    }

    public List<LedgerEntry> findByOrder(int orderId) throws SQLException {
        List<LedgerEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM financial_ledger WHERE order_id = ? ORDER BY id ASC")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapEntry(rs));
            }
        }
        return list;
    }

    public List<LedgerEntry> findBySupplier(int supplierId, int limit) throws SQLException {
        List<LedgerEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM financial_ledger WHERE supplier_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, supplierId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapEntry(rs));
            }
        }
        return list;
    }

    /** Sommes EARNED − REVERSED : GMV, commissions, revenus. */
    public long[] platformTotals() throws SQLException {
        String sql = "SELECT "
            + "COALESCE(SUM(gross),0), "
            + "COALESCE(SUM(commission + fixed_fee),0), "
            + "COALESCE(SUM(supplier_earnings),0), "
            + "COUNT(DISTINCT CASE WHEN status='EARNED' THEN order_id END) "
            + "FROM financial_ledger";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)};
            return new long[]{0, 0, 0, 0};
        }
    }

    /**
     * Totaux comptabilisés par période, nets des contre-passations.
     * Clés "today","week7","month","year" → [gross, commission+fixed, net].
     */
    public java.util.Map<String, long[]> supplierPeriods(int supplierId) throws SQLException {
        String sql = "SELECT "
            + "SUM(CASE WHEN created_at >= CURDATE() THEN gross ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= CURDATE() THEN (commission + fixed_fee) ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= CURDATE() THEN supplier_earnings ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= CURDATE() - INTERVAL 7 DAY THEN gross ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= CURDATE() - INTERVAL 7 DAY THEN (commission + fixed_fee) ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= CURDATE() - INTERVAL 7 DAY THEN supplier_earnings ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN gross ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN (commission + fixed_fee) ELSE 0 END), "
            + "SUM(CASE WHEN created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN supplier_earnings ELSE 0 END), "
            + "SUM(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) THEN gross ELSE 0 END) "
            + "FROM financial_ledger WHERE supplier_id = ?";
        java.util.Map<String, long[]> out = new java.util.LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    out.put("today", new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)});
                    out.put("week7", new long[]{rs.getLong(4), rs.getLong(5), rs.getLong(6)});
                    out.put("month", new long[]{rs.getLong(7), rs.getLong(8), rs.getLong(9)});
                    out.put("year", new long[]{rs.getLong(10), 0, 0});
                }
            }
        }
        return out;
    }

    public long[] supplierTotals(int supplierId) throws SQLException {        String sql = "SELECT "
            + "COALESCE(SUM(gross),0), "
            + "COALESCE(SUM(commission + fixed_fee),0), "
            + "COALESCE(SUM(supplier_earnings),0), "
            + "COUNT(DISTINCT CASE WHEN status='EARNED' THEN order_id END) "
            + "FROM financial_ledger WHERE supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)};
            }
            return new long[]{0, 0, 0, 0};
        }
    }

    private MonetizationRule mapRule(ResultSet rs) throws SQLException {
        MonetizationRule r = new MonetizationRule();
        r.setId(rs.getInt("id"));
        r.setName(rs.getString("name"));
        r.setCommissionBps(rs.getInt("commission_bps"));
        r.setFixedFee(rs.getInt("fixed_fee"));
        r.setActive(rs.getBoolean("is_active"));
        return r;
    }

    private LedgerEntry mapEntry(ResultSet rs) throws SQLException {
        LedgerEntry e = new LedgerEntry();
        e.setId(rs.getInt("id"));
        e.setReference(rs.getString("reference"));
        e.setOrderId(rs.getInt("order_id"));
        int sid = rs.getInt("supplier_id");
        e.setSupplierId(rs.wasNull() ? null : sid);
        e.setKind(rs.getString("kind"));
        e.setGross(rs.getInt("gross"));
        e.setDiscount(rs.getInt("discount"));
        e.setNet(rs.getInt("net"));
        e.setCommissionBps(rs.getInt("commission_bps"));
        e.setCommission(rs.getInt("commission"));
        e.setFixedFee(rs.getInt("fixed_fee"));
        e.setSupplierEarnings(rs.getInt("supplier_earnings"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(rs.getString("created_at"));
        return e;
    }
}
