package com.ecommerce.service;

import com.ecommerce.config.DBConnection;
import com.ecommerce.dao.ActivityLogDAO;
import com.ecommerce.dao.ConversationDAO;
import com.ecommerce.dao.OfferDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.ReviewDAO;
import com.ecommerce.dao.ShipmentDAO;
import com.ecommerce.model.Review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supervision admin / Control Center (Phase 14).
 * Que des données réelles : COUNT ciblés + listes paginées, jamais de N+1,
 * jamais de chiffres inventés (donnée absente = champ omis, pas 0 déguisé).
 */
public class AdminControlService {

    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final OfferDAO offerDAO = new OfferDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();

    /**
     * Tableau de bord SYNCHRONISÉ (espace admin + client + fournisseur).
     * UNE seule connexion, UN seul instant T : tous les compteurs sont
     * cohérents entre eux (pas de dérive entre 4 appels séparés).
     * - Commandes : source unique (statuts + revenus non annulés + jour).
     * - Finance : ledger comptable à côté des revenus commandes (labels
     *   explicites : encours vs comptabilisé, jamais confondus).
     * - Clients : comptes enregistrés (table customers, espace client)
     *   + invités (téléphones uniques des commandes).
     * - Fournisseurs : réconciliation commandes vs ledger par boutique
     *   (mêmes chiffres que l'espace fournisseur).
     */
    public Map<String, Object> syncedDashboard() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection()) {
            out.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));

            // ---- Commandes : ventilation unique par statut ----
            Map<String, Object> orders = new LinkedHashMap<>();
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            Map<String, Long> revenueByStatus = new LinkedHashMap<>();
            int totalOrders = 0;
            long revenueOrders = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, COUNT(*), COALESCE(SUM(total),0) FROM orders GROUP BY status");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String st = rs.getString(1);
                    int c = rs.getInt(2);
                    long r = rs.getLong(3);
                    byStatus.put(st, c);
                    revenueByStatus.put(st, r);
                    totalOrders += c;
                    if (!"ANNULEE".equals(st)) revenueOrders += r;
                }
            }
            orders.put("total", totalOrders);
            orders.put("byStatus", byStatus);
            orders.put("revenueByStatus", revenueByStatus);
            // Revenus encours = commandes non annulées (avant comptabilisation).
            orders.put("revenuePending", revenueOrders);
            orders.put("today", count(conn,
                "SELECT COUNT(*) FROM orders WHERE created_at >= CURDATE() AND created_at < CURDATE() + INTERVAL 1 DAY"));
            orders.put("todayRevenue", scalar(conn,
                "SELECT COALESCE(SUM(total),0) FROM orders WHERE created_at >= CURDATE()"
                + " AND created_at < CURDATE() + INTERVAL 1 DAY AND status != 'ANNULEE'"));
            out.put("orders", orders);

            // ---- Finance ledger (comptabilisé) : à côté, jamais mélangé ----
            Map<String, Object> finance = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(gross),0), COALESCE(SUM(commission + fixed_fee),0),"
                     + " COALESCE(SUM(supplier_earnings),0),"
                     + " COUNT(DISTINCT CASE WHEN status='EARNED' THEN order_id END)"
                     + " FROM financial_ledger");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    finance.put("gmvSettled", rs.getLong(1));
                    finance.put("platformSettled", rs.getLong(2));
                    finance.put("suppliersSettled", rs.getLong(3));
                    finance.put("settledOrders", rs.getLong(4));
                }
            }
            finance.put("note", "Encours (commandes non annulées) vs comptabilisé (ledger) :"
                + " l'écart = commandes pas encore livrées/soldées.");
            out.put("finance", finance);

            // ---- Clients : espace client (comptes) + invités (commandes) ----
            Map<String, Object> clients = new LinkedHashMap<>();
            clients.put("registered", count(conn, "SELECT COUNT(*) FROM customers"));
            clients.put("guestPhones", count(conn, "SELECT COUNT(DISTINCT phone) FROM orders"));
            out.put("clients", clients);

            // ---- Fournisseurs : réconciliation commandes vs ledger ----
            Map<String, Object> suppliers = new LinkedHashMap<>();
            suppliers.put("total", count(conn, "SELECT COUNT(*) FROM suppliers"));
            suppliers.put("active", count(conn, "SELECT COUNT(*) FROM suppliers WHERE is_active=1"));
            suppliers.put("pendingProducts",
                count(conn, "SELECT COUNT(*) FROM products WHERE status='PENDING'"));
            List<Map<String, Object>> recon = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT s.id, s.name, s.is_active, s.balance, s.pending_balance,"
                     + " (SELECT COUNT(*) FROM orders o WHERE o.supplier_id = s.id) AS nb_orders,"
                     + " (SELECT COALESCE(SUM(o.total),0) FROM orders o"
                     + "   WHERE o.supplier_id = s.id AND o.status != 'ANNULEE') AS rev_orders,"
                     + " (SELECT COALESCE(SUM(l.supplier_earnings),0) FROM financial_ledger l"
                     + "   WHERE l.supplier_id = s.id) AS net_ledger,"
                     + " (SELECT COUNT(*) FROM products p WHERE p.supplier_id = s.id) AS nb_products"
                     + " FROM suppliers s ORDER BY s.name LIMIT 200");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getInt("id"));
                    r.put("name", rs.getString("name"));
                    r.put("active", rs.getInt("is_active") == 1);
                    r.put("orders", rs.getInt("nb_orders"));
                    r.put("revenueOrders", rs.getLong("rev_orders"));
                    r.put("netLedger", rs.getLong("net_ledger"));
                    r.put("balance", rs.getLong("balance"));
                    r.put("pendingBalance", rs.getLong("pending_balance"));
                    r.put("products", rs.getInt("nb_products"));
                    recon.add(r);
                }
            }
            suppliers.put("reconciliation", recon);
            // Commandes sans fournisseur affecté (panier multi-boutiques).
            suppliers.put("ordersWithoutSupplier",
                count(conn, "SELECT COUNT(*) FROM orders WHERE supplier_id IS NULL"));
            out.put("suppliers", suppliers);

            // ---- Catalogue / modération ----
            Map<String, Object> catalog = new LinkedHashMap<>();
            catalog.put("products", count(conn, "SELECT COUNT(*) FROM products"));
            catalog.put("lowStock", count(conn,
                "SELECT COUNT(*) FROM products WHERE stock <= 5 AND stock > 0"));
            catalog.put("outOfStock", count(conn, "SELECT COUNT(*) FROM products WHERE stock = 0"));
            catalog.put("categories", count(conn, "SELECT COUNT(*) FROM categories"));
            catalog.put("pendingReviews", count(conn, "SELECT COUNT(*) FROM reviews WHERE approved=0"));
            out.put("catalog", catalog);

            // ---- Messagerie / négociation (client <-> fournisseur) ----
            Map<String, Object> messaging = new LinkedHashMap<>();
            messaging.put("conversations", count(conn, "SELECT COUNT(*) FROM conversations"));
            messaging.put("unreadMessages", count(conn,
                "SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id"
                + " WHERE m.sender='CLIENT' AND m.is_read=0"));
            messaging.put("pendingOffers", count(conn, "SELECT COUNT(*) FROM offers WHERE status='PENDING'"));
            out.put("messaging", messaging);

            // ---- Logistique / paiements / versements ----
            Map<String, Object> ops = new LinkedHashMap<>();
            ops.put("activeShipments", count(conn,
                "SELECT COUNT(*) FROM shipments WHERE status NOT IN ('DELIVERED','CANCELLED','RETURNED')"));
            ops.put("shipmentIncidents", count(conn,
                "SELECT COUNT(*) FROM shipments WHERE status IN ('FAILED','RETURNED')"));
            ops.put("pendingPayments", count(conn, "SELECT COUNT(*) FROM payments WHERE status='EN_ATTENTE'"));
            ops.put("failedPayments", count(conn, "SELECT COUNT(*) FROM payments WHERE status='ECHOUEE'"));
            ops.put("pendingPayouts", count(conn,
                "SELECT COUNT(*) FROM supplier_payments WHERE status IN ('PENDING','PROCESSING')"));
            ops.put("unreadNotifications", count(conn, "SELECT COUNT(*) FROM notifications WHERE is_read=0"));
            out.put("ops", ops);
        }
        return out;
    }

    private long scalar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        }
        return 0;
    }

    /** KPIs de supervision (complètent /dashboard, pas de doublon). */
    public Map<String, Object> overview() throws SQLException {        Map<String, Object> m = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection()) {
            m.put("distinctClients", count(conn, "SELECT COUNT(DISTINCT phone) FROM orders"));
            m.put("unreadMessages", conversationDAO.unreadTotal());
            m.put("conversations", conversationDAO.countAll());
            m.put("pendingOffers", offerDAO.countPending());
            m.put("failedPayments", paymentDAO.countByStatus("ECHOUEE"));
            m.put("pendingPayments", paymentDAO.countByStatus("EN_ATTENTE"));
            m.put("refundedPayments", paymentDAO.countByStatus("ANNULEE"));
            m.put("activeShipments", shipmentDAO.countByStatusGroup("active"));
            m.put("shipmentIncidents", shipmentDAO.countByStatusGroup("incidents"));
            m.put("pendingProducts", count(conn, "SELECT COUNT(*) FROM products WHERE status='PENDING'"));
            m.put("pendingReviews", count(conn, "SELECT COUNT(*) FROM reviews WHERE approved=0"));
            m.put("pendingPayouts", count(conn,
                "SELECT COUNT(*) FROM supplier_payments WHERE status IN ('PENDING','PROCESSING')"));
            m.put("unreadNotifications", count(conn,
                "SELECT COUNT(*) FROM notifications WHERE is_read=0"));
        }
        return m;
    }

    public Map<String, Object> payments(String status, int page, int limit) throws SQLException {
        limit = clamp(limit);
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", paymentDAO.findPagedWithOrder(normStatus(status), limit, offset));
        m.put("total", paymentDAO.countByStatus(normStatus(status)));
        m.put("page", Math.max(1, page));
        m.put("limit", limit);
        return m;
    }

    public Map<String, Object> shipments(String filter, int limit) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        String f = "incidents".equals(filter) ? "incidents" : "all".equals(filter) ? "all" : "active";
        m.put("data", shipmentDAO.findForAdmin(f, clamp(limit)));
        m.put("active", shipmentDAO.countByStatusGroup("active"));
        m.put("incidents", shipmentDAO.countByStatusGroup("incidents"));
        return m;
    }

    public Map<String, Object> conversations(int page, int limit) throws SQLException {
        limit = clamp(limit);
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", conversationDAO.findAllForAdmin(limit, offset));
        m.put("total", conversationDAO.countAll());
        m.put("unread", conversationDAO.unreadTotal());
        m.put("page", Math.max(1, page));
        m.put("limit", limit);
        return m;
    }

    public Map<String, Object> pendingOffers(int page, int limit) throws SQLException {
        limit = clamp(limit);
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", offerDAO.findAllPendingForAdmin(limit, offset));
        m.put("total", offerDAO.countPending());
        m.put("page", Math.max(1, page));
        m.put("limit", limit);
        return m;
    }

    /** Avis : pending ou all, plafonné (volumes faibles, pas de pagination lourde). */
    public List<Review> reviews(String status) throws SQLException {
        List<Review> all = "pending".equals(status) ? reviewDAO.findPending() : reviewDAO.findAll();
        return all.size() > 200 ? all.subList(0, 200) : all;
    }

    public Map<String, Object> audit(String action, int page, int limit) throws SQLException {
        limit = clamp(limit);
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", activityLogDAO.findPaged(emptyToNull(action), limit, offset));
        m.put("total", activityLogDAO.count(emptyToNull(action)));
        m.put("page", Math.max(1, page));
        m.put("limit", limit);
        return m;
    }

    private int count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(100, limit));
    }

    private String normStatus(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String u = s.trim().toUpperCase();
        if ("EN_ATTENTE".equals(u) || "REUSSIE".equals(u) || "ECHOUEE".equals(u) || "ANNULEE".equals(u)) return u;
        return null;
    }

    private String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
