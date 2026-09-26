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

    /** KPIs de supervision (complètent /dashboard, pas de doublon). */
    public Map<String, Object> overview() throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
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
