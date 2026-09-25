package com.ecommerce.service;

import com.ecommerce.config.DBConnection;
import com.ecommerce.dao.MonetizationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.model.LedgerEntry;
import com.ecommerce.model.MonetizationRule;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Monétisation Phase 10.
 * Base = montant réellement transacté (lignes, prix négociés inclus).
 * Règle figée par écriture (anti-rétroactivité). Idempotent par référence.
 * Arithmétique entière FCFA, HALF_UP : (net * bps + 5000) / 10000.
 */
public class MonetizationService {

    private final MonetizationDAO monetizationDAO = new MonetizationDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    public static int commissionOf(int net, int bps) {
        if (net <= 0 || bps <= 0) return 0;
        return (int) ((net * (long) bps + 5000L) / 10000L);
    }

    public MonetizationRule currentRule() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return monetizationDAO.resolveRule(conn);
        }
    }

    public List<MonetizationRule> rules() throws SQLException {
        return monetizationDAO.findAllRules();
    }

    public int createRule(String name, int bps, int fixedFee) throws AppException, SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Nom de règle obligatoire");
        }
        if (bps < 0 || bps > 10000) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commission 0–10000 bps");
        }
        if (fixedFee < 0) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Frais fixes invalides");
        }
        MonetizationRule r = new MonetizationRule();
        r.setName(name.trim().substring(0, Math.min(120, name.trim().length())));
        r.setCommissionBps(bps);
        r.setFixedFee(fixedFee);
        r.setActive(false);
        return monetizationDAO.createRule(r);
    }

    public void setRuleActive(int id, boolean active) throws SQLException {
        monetizationDAO.setRuleActive(id, active);
    }

    /**
     * Comptabilise une commande LIVREE. Idempotent (clé LED-<id>-SALE).
     * À appeler après le passage au statut LIVREE (retry sûr).
     */
    public LedgerEntry settleDeliveredOrder(int orderId) throws AppException, SQLException {
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        if (!"LIVREE".equals(o.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Commande non livrée");
        }
        if (monetizationDAO.hasEntries(orderId, "SALE")) {
            List<LedgerEntry> existing = monetizationDAO.findByOrder(orderId);
            for (LedgerEntry e : existing) {
                if ("SALE".equals(e.getKind()) && "EARNED".equals(e.getStatus())) return e;
            }
        }
        int gross = 0;
        int discount = 0;
        if (o.getItems() != null) {
            for (OrderItem it : o.getItems()) {
                gross += it.getQty() * it.getUnitPrice();
            }
        }
        // Remise vs catalogue : offres liées uniquement (données réelles).
        discount = discountForOrder(orderId);
        int net = gross;
        MonetizationRule rule;
        try (Connection conn = DBConnection.getConnection()) {
            rule = monetizationDAO.resolveRule(conn);
        }
        int commission = commissionOf(net, rule.getCommissionBps());
        int fixedFee = Math.max(0, rule.getFixedFee());
        int supplierEarnings = Math.max(0, net - commission - fixedFee);

        LedgerEntry e = new LedgerEntry();
        e.setReference("LED-" + orderId + "-SALE");
        e.setOrderId(orderId);
        e.setSupplierId(o.getSupplierId() > 0 ? o.getSupplierId() : null);
        e.setKind("SALE");
        e.setGross(gross);
        e.setDiscount(discount);
        e.setNet(net);
        e.setCommissionBps(rule.getCommissionBps());
        e.setFixedFee(fixedFee);
        e.setCommission(commission);
        e.setSupplierEarnings(supplierEarnings);
        e.setStatus("EARNED");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                monetizationDAO.insert(conn, e);
                if (e.getSupplierId() != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE suppliers SET balance = balance + ? WHERE id = ?")) {
                        ps.setInt(1, supplierEarnings);
                        ps.setInt(2, e.getSupplierId());
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
            // Double traitement : la référence unique protège, on relit.
            List<LedgerEntry> existing = monetizationDAO.findByOrder(orderId);
            for (LedgerEntry en : existing) {
                if ("SALE".equals(en.getKind())) return en;
            }
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", dup);
        }
        return e;
    }

    private int discountForOrder(int orderId) throws SQLException {
        String sql = "SELECT COALESCE(SUM((off.catalog_price - off.unit_price) * off.qty), 0) "
            + "FROM order_items oi JOIN offers off ON off.id = oi.offer_id WHERE oi.order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getInt(1));
            }
        }
        return 0;
    }

    /**
     * Contre-passation (annulation/suppression post-livraison).
     * Nouvelle ligne REVERSAL, jamais de modification/suppression.
     */
    public LedgerEntry reverseOrder(int orderId, String reason) throws AppException, SQLException {
        List<LedgerEntry> existing = monetizationDAO.findByOrder(orderId);
        LedgerEntry sale = null;
        for (LedgerEntry e : existing) {
            if ("SALE".equals(e.getKind()) && "EARNED".equals(e.getStatus())) { sale = e; break; }
        }
        if (sale == null) return null; // rien à contre-passer
        if (monetizationDAO.hasEntries(orderId, "REVERSAL")) {
            for (LedgerEntry e : existing) {
                if ("REVERSAL".equals(e.getKind())) return e;
            }
        }
        Order o;
        try {
            o = orderDAO.findById(orderId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LedgerEntry r = new LedgerEntry();
        r.setReference("LED-" + orderId + "-REVERSAL");
        r.setOrderId(orderId);
        r.setSupplierId(sale.getSupplierId());
        r.setKind("REVERSAL");
        r.setGross(-sale.getGross());
        r.setDiscount(-sale.getDiscount());
        r.setNet(-sale.getNet());
        r.setCommissionBps(sale.getCommissionBps());
        r.setCommission(-sale.getCommission());
        r.setFixedFee(-sale.getFixedFee());
        r.setSupplierEarnings(-sale.getSupplierEarnings());
        r.setStatus("REVERSED");
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                monetizationDAO.insert(conn, r);
                if (r.getSupplierId() != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE suppliers SET balance = balance + ? WHERE id = ?")) {
                        ps.setInt(1, r.getSupplierEarnings());
                        ps.setInt(2, r.getSupplierId());
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
            List<LedgerEntry> again = monetizationDAO.findByOrder(orderId);
            for (LedgerEntry en : again) {
                if ("REVERSAL".equals(en.getKind())) return en;
            }
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", dup);
        }
        return r;
    }

    public List<LedgerEntry> entriesForOrder(int orderId) throws SQLException {
        return monetizationDAO.findByOrder(orderId);
    }

    public List<LedgerEntry> entriesForSupplier(int supplierId, int limit) throws SQLException {
        return monetizationDAO.findBySupplier(supplierId, Math.max(1, Math.min(100, limit)));
    }

    /**
     * Recalcule tous les soldes depuis le ledger (maintenance admin).
     * À utiliser après correction manuelle ou import ; les écritures
     * restent la source de vérité, jamais l'inverse.
     * @return nombre de fournisseurs recalculés.
     */
    public int rebuildBalances() throws SQLException {
        String sql = "UPDATE suppliers s LEFT JOIN "
            + "(SELECT supplier_id, COALESCE(SUM(supplier_earnings),0) AS b FROM financial_ledger GROUP BY supplier_id) t "
            + "ON t.supplier_id = s.id SET s.balance = COALESCE(t.b, 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public Map<String, Object> platformOverview() throws SQLException {
        long[] t = monetizationDAO.platformTotals();        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("gmv", t[0]);
        m.put("platformEarnings", t[1]);
        m.put("supplierEarnings", t[2]);
        m.put("settledOrders", t[3]);
        return m;
    }

    public Map<String, Object> supplierOverview(int supplierId) throws SQLException {        long[] t = monetizationDAO.supplierTotals(supplierId);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("gmv", t[0]);
        m.put("commission", t[1]);
        m.put("netEarnings", t[2]);
        m.put("settledOrders", t[3]);
        return m;
    }
}
