package com.ecommerce.controller;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Admin;
import com.ecommerce.service.AdminService;
import com.ecommerce.dao.AdminSessionDAO;
import com.ecommerce.dao.ActivityLogDAO;
import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.model.Product;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/admin/*")
public class AdminServlet extends HttpServlet {
    private final AdminService adminService = new AdminService();
    private final AdminSessionDAO sessionDAO = new AdminSessionDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    private String extractToken(HttpServletRequest req) {
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) return token.substring(7);
        // Header seul : le token en query (?token=) fuit dans logs/historique/Referer.
        return null;
    }

    private int validateAndGetAdminId(HttpServletRequest req) {
        try {
            return sessionDAO.validateSession(extractToken(req));
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String pathInfo = req.getPathInfo();
            String action = req.getParameter("action");

            if ("/login".equals(pathInfo) || "login".equals(action)) {
                // Handled in doPost
                resp.setStatus(405);
                resp.getWriter().write("{\"error\":\"Use POST for login\"}");
                return;
            }

            // Toutes les autres lectures admin exigent une session valide.
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;

            if ("/logout".equals(pathInfo) || "logout".equals(action)) {
                String token = extractToken(req);
                if (token != null) sessionDAO.invalidateSession(token);
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            if ("/dashboard".equals(pathInfo) || "dashboard".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(getDashboard()));
                return;
            }

            if ("/stats".equals(pathInfo) || "stats".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(getStats()));
                return;
            }

            if ("/logs".equals(pathInfo) || "logs".equals(action)) {
                int limit = 50;
                try { limit = Integer.parseInt(req.getParameter("limit")); } catch (Exception ignored) {}
                resp.getWriter().write(JsonUtil.toJson(activityLogDAO.findRecent(limit)));
                return;
            }

            if ("/finance".equals(pathInfo) || "finance".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(new com.ecommerce.service.MonetizationService().platformOverview()));
                return;
            }

            if ("/monetization/rules".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(new com.ecommerce.service.MonetizationService().rules()));
                return;
            }

            // Identité réelle (rôle résolu serveur, jamais choisi par le frontend).
            if ("/me".equals(pathInfo)) {
                int meId = validateAndGetAdminId(req);
                com.ecommerce.model.Admin me = new com.ecommerce.dao.AdminDAO().findById(meId);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", meId);
                m.put("username", me == null ? null : me.getUsername());
                m.put("role", me == null || me.getRole() == null ? "ADMIN" : me.getRole());
                resp.getWriter().write(JsonUtil.toJson(m));
                return;
            }

            // Control Center : supervision (tous rôles authentifiés, lecture seule).
            com.ecommerce.service.AdminControlService control = new com.ecommerce.service.AdminControlService();
            if ("/overview".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.overview()));
                return;
            }
            if ("/payments".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.payments(
                    req.getParameter("status"), parseInt(req.getParameter("page"), 1),
                    parseInt(req.getParameter("limit"), 20))));
                return;
            }
            if ("/shipments".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.shipments(
                    req.getParameter("filter"), parseInt(req.getParameter("limit"), 50))));
                return;
            }
            if ("/conversations".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.conversations(
                    parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("limit"), 20))));
                return;
            }
            if ("/offers/pending".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.pendingOffers(
                    parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("limit"), 20))));
                return;
            }
            if ("/reviews".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.reviews(req.getParameter("status"))));
                return;
            }
            if ("/audit".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.audit(
                    req.getParameter("action"), parseInt(req.getParameter("page"), 1),
                    parseInt(req.getParameter("limit"), 20))));
                return;
            }

            // Default: dashboard
            resp.getWriter().write(JsonUtil.toJson(getDashboard()));
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String pathInfo = req.getPathInfo();
            String action = req.getParameter("action");

            if ("/login".equals(pathInfo) || "login".equals(action)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, String> body = JsonUtil.fromJson(sb.toString(), Map.class);

                String username = body.get("username");
                String password = body.get("password");

                // Anti brute-force : 5 échecs → 15 min (clé username + IP).
                String attemptKey = com.ecommerce.service.LoginAttemptService.key(
                    "admin", username, req.getRemoteAddr());
                if (com.ecommerce.service.LoginAttemptService.get().isBlocked(attemptKey)) {
                    resp.setStatus(429);
                    resp.getWriter().write("{\"error\":\"Trop de tentatives. Réessayez dans 15 minutes.\"}");
                    return;
                }

                Admin admin = adminService.login(username, password);
                if (admin != null) {
                    String token = sessionDAO.createSession(
                        admin.getId(),
                        req.getRemoteAddr(),
                        req.getHeader("User-Agent"),
                        480 // 8 hours
                    );
                    // Log activity
                    activityLogDAO.log(admin.getId(), "LOGIN", "admin", admin.getId(), "Connexion réussie", req.getRemoteAddr());
                    resp.getWriter().write("{\"token\":\"" + token + "\",\"username\":\"" + admin.getUsername() + "\",\"role\":\"" + admin.getRole() + "\"}");
                    com.ecommerce.service.LoginAttemptService.get().recordSuccess(attemptKey);
                } else {
                    com.ecommerce.service.LoginAttemptService.get().recordFailure(attemptKey);
                    resp.setStatus(401);
                    resp.getWriter().write("{\"error\":\"Identifiants incorrects\"}");
                }
                return;
            }

            if ("/logout".equals(pathInfo) || "logout".equals(action)) {
                int adminId = validateAndGetAdminId(req);
                String token = extractToken(req);
                if (token != null) sessionDAO.invalidateSession(token);
                if (adminId > 0) activityLogDAO.log(adminId, "LOGOUT", "admin", adminId, "Déconnexion", req.getRemoteAddr());
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            if ("/product/validate".equals(pathInfo) || "validateProduct".equals(action)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = validateAndGetAdminId(req);
                if (adminId <= 0) { resp.setStatus(401); resp.getWriter().write("{\"error\":\"Non autorisé\"}"); return; }

                int productId = body.get("productId") != null ? ((Number) body.get("productId")).intValue() : 0;
                boolean approved = body.get("approved") == null || Boolean.parseBoolean(body.get("approved").toString());
                if (productId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"produit requis\"}"); return; }

                String productName = "";
                java.sql.Connection conn = null;
                try {
                    conn = DBConnection.getConnection();
                    ProductDAO productDAO = new ProductDAO();
                    Product p = productDAO.findById(productId);
                    if (p == null) { resp.setStatus(404); resp.getWriter().write("{\"error\":\"Produit introuvable\"}"); return; }
                    String newStatus = approved ? "APPROVED" : "REJECTED";
                    productName = p.getName() == null ? "" : p.getName();
                    productDAO.updateSupplierProductStatus(productId, newStatus);

                    int supplierId = p.getSupplierId();
                    if (supplierId > 0) {
                        try {
                            new com.ecommerce.service.NotificationService().notifySupplier(supplierId,
                                approved ? "PRODUCT_APPROVED" : "PRODUCT_REJECTED",
                                approved ? "Produit validé" : "Produit refusé",
                                approved
                                    ? "Votre produit '" + productName + "' est maintenant en ligne sur la boutique."
                                    : "Votre produit '" + productName + "' a été refusé par l'administrateur.",
                                "PRODUCT", productId, null,
                                "PRODUCT:" + productId + ":VALIDATED:" + (approved ? "APPROVED" : "REJECTED"));
                        } catch (Exception ignored) {}
                    }
                } finally {
                    if (conn != null) conn.close();
                }
                activityLogDAO.log(adminId, approved ? "PRODUCT_APPROVED" : "PRODUCT_REJECTED", "product", productId,
                    "Produit '" + productName + "' " + (approved ? "approuvé" : "refusé"), req.getRemoteAddr());

                resp.getWriter().write("{\"success\":true,\"status\":\"" + (approved ? "APPROVED" : "REJECTED") + "\"}");
                return;
            }

            if ("/monetization/rules".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                com.ecommerce.service.MonetizationService monetization = new com.ecommerce.service.MonetizationService();
                int ruleId = monetization.createRule(
                    body.get("name") == null ? null : String.valueOf(body.get("name")),
                    body.get("commissionBps") instanceof Number ? ((Number) body.get("commissionBps")).intValue() : -1,
                    body.get("fixedFee") instanceof Number ? ((Number) body.get("fixedFee")).intValue() : 0);
                activityLogDAO.log(adminId, "MONETIZATION_RULE_CREATED", "monetization_rule", ruleId,
                    "Règle créée", req.getRemoteAddr());
                resp.setStatus(201);
                resp.getWriter().write("{\"success\":true,\"id\":" + ruleId + "}");
                return;
            }

            // Mot de passe du compte connecté (tous rôles, vérification de l'actuel).
            if ("/password".equals(pathInfo)) {
                int pwdAdmin = validateAndGetAdminId(req);
                if (pwdAdmin <= 0) { resp.setStatus(401); resp.getWriter().write("{\"error\":\"Non autorisé\"}"); return; }
                BufferedReader reader0 = req.getReader();
                StringBuilder sb0 = new StringBuilder();
                String line0;
                while ((line0 = reader0.readLine()) != null) sb0.append(line0);
                Map<String, Object> body0 = JsonUtil.fromJson(sb0.toString(), Map.class);
                String current = body0.get("current") == null ? null : String.valueOf(body0.get("current"));
                String fresh = body0.get("new") == null ? null : String.valueOf(body0.get("new"));
                if (current == null || current.isEmpty() || fresh == null || fresh.length() < 8) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Mot de passe actuel requis, nouveau min 8 caractères\"}");
                    return;
                }
                boolean ok = new com.ecommerce.dao.AdminDAO().updatePassword(pwdAdmin, current, fresh);
                if (!ok) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Mot de passe actuel incorrect\"}");
                    return;
                }
                activityLogDAO.log(pwdAdmin, "ADMIN_PASSWORD_CHANGED", "admin", pwdAdmin,
                    "Mot de passe modifié", req.getRemoteAddr());
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            // Remboursement FINANCIER uniquement (Phase 10) : contre-passation
            // comptable, aucun mouvement d'argent (Phase 11).
            if ("/orders/refund".equals(pathInfo)) {                BufferedReader reader2 = req.getReader();
                StringBuilder sb2 = new StringBuilder();
                String line2;
                while ((line2 = reader2.readLine()) != null) sb2.append(line2);
                Map<String, Object> body2 = JsonUtil.fromJson(sb2.toString(), Map.class);

                int adminId2 = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId2 <= 0) return;

                int orderId = body2.get("id") instanceof Number ? ((Number) body2.get("id")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"Commande requise\"}"); return; }
                try {
                    com.ecommerce.model.LedgerEntry rev =
                        new com.ecommerce.service.MonetizationService().reverseOrder(orderId, "ADMIN_REFUND");
                    if (rev == null) {
                        resp.setStatus(409);
                        resp.getWriter().write("{\"success\":false,\"message\":\"Rien à rembourser\",\"errorCode\":\"CONFLICT\"}");
                        return;
                    }
                    // Le remboursement PSP reste manuel (documenté) ; le statut suit ici.
                    try {
                        new com.ecommerce.service.PaymentService().markRefunded(orderId, true);
                    } catch (Exception ignored) {}
                    activityLogDAO.log(adminId2, "ORDER_REFUNDED", "order", orderId,
                        "Remboursement comptable", req.getRemoteAddr());
                    resp.getWriter().write("{\"success\":true,\"reference\":\"" + rev.getReference() + "\"}");
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }

            if ("/finance/rebuild".equals(pathInfo)) {
                int adminId3 = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId3 <= 0) return;
                int n = new com.ecommerce.service.MonetizationService().rebuildBalances();
                activityLogDAO.log(adminId3, "FINANCE_REBUILT", "supplier", 0,
                    n + " soldes recalculés", req.getRemoteAddr());
                resp.getWriter().write("{\"success\":true,\"rebuilt\":" + n + "}");
                return;
            }

            if ("/monetization/rules/activate".equals(pathInfo)) {                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int ruleId = body.get("id") instanceof Number ? ((Number) body.get("id")).intValue() : 0;
                boolean active = Boolean.TRUE.equals(body.get("active")) || "true".equals(String.valueOf(body.get("active")));
                if (ruleId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"Règle requise\"}"); return; }
                new com.ecommerce.service.MonetizationService().setRuleActive(ruleId, active);
                activityLogDAO.log(adminId, active ? "MONETIZATION_RULE_ACTIVATED" : "MONETIZATION_RULE_DEACTIVATED",
                    "monetization_rule", ruleId, "Règle " + (active ? "activée" : "désactivée"), req.getRemoteAddr());
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Action requise\"}");
        } catch (com.ecommerce.util.AppException ae) {
            com.ecommerce.util.ApiResponse.fromException(resp, ae);
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String token = extractToken(req);
            if (token != null) sessionDAO.invalidateSession(token);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    private int parseInt(String s, int def) {
        try {
            if (s == null) return def;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Map<String, Object> getDashboard() throws SQLException {        Map<String, Object> dash = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection()) {
            dash.put("totalProducts", count(conn, "SELECT COUNT(*) FROM products"));
            dash.put("totalSuppliers", count(conn, "SELECT COUNT(*) FROM suppliers"));
            dash.put("totalOrders", count(conn, "SELECT COUNT(*) FROM orders"));
            dash.put("pendingOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='EN_ATTENTE'"));
            dash.put("confirmedOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='CONFIRMEE'"));
            dash.put("preparingOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='EN_PREPARATION'"));
            dash.put("shippingOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='EN_LIVRAISON'"));
            dash.put("deliveredOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='LIVREE'"));
            dash.put("cancelledOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE status='ANNULEE'"));
            dash.put("totalCategories", count(conn, "SELECT COUNT(*) FROM categories"));
            dash.put("totalReviews", count(conn, "SELECT COUNT(*) FROM reviews"));
            dash.put("pendingReviews", count(conn, "SELECT COUNT(*) FROM reviews WHERE approved=0"));
            dash.put("totalRevenue", count(conn, "SELECT COALESCE(SUM(total),0) FROM orders WHERE status IN ('CONFIRMEE','EN_PREPARATION','EN_LIVRAISON','LIVREE')"));
            dash.put("todayOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE DATE(created_at) = CURDATE()"));
            dash.put("todayRevenue", count(conn, "SELECT COALESCE(SUM(total),0) FROM orders WHERE DATE(created_at) = CURDATE() AND status != 'ANNULEE'"));
            dash.put("lowStockProducts", count(conn, "SELECT COUNT(*) FROM products WHERE stock <= 5 AND stock > 0"));
            dash.put("outOfStockProducts", count(conn, "SELECT COUNT(*) FROM products WHERE stock = 0"));
            dash.put("totalPayments", count(conn, "SELECT COUNT(*) FROM payments"));
            dash.put("successfulPayments", count(conn, "SELECT COUNT(*) FROM payments WHERE status='REUSSIE'"));
            dash.put("pendingNotifications", count(conn, "SELECT COUNT(*) FROM notifications WHERE is_read=0"));
        }
        return dash;
    }

    private Map<String, Object> getStats() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection()) {
            // Top products by sales
            List<Map<String, Object>> topProducts = new ArrayList<>();
            String sqlTop = "SELECT p.name, p.brand, SUM(oi.qty) as total_sold, SUM(oi.unit_price * oi.qty) as revenue "
                + "FROM order_items oi JOIN products p ON oi.product_id = p.id "
                + "JOIN orders o ON oi.order_id = o.id WHERE o.status != 'ANNULEE' "
                + "GROUP BY oi.product_id ORDER BY total_sold DESC LIMIT 10";
            try (PreparedStatement ps = conn.prepareStatement(sqlTop);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("brand", rs.getString("brand"));
                    row.put("totalSold", rs.getInt("total_sold"));
                    row.put("revenue", rs.getInt("revenue"));
                    topProducts.add(row);
                }
            }
            stats.put("topProducts", topProducts);

            // Orders by status
            List<Map<String, Object>> ordersByStatus = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT status, COUNT(*) as count FROM orders GROUP BY status");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", rs.getString("status"));
                    row.put("count", rs.getInt("count"));
                    ordersByStatus.add(row);
                }
            }
            stats.put("ordersByStatus", ordersByStatus);

            // Orders by day (last 30 days)
            List<Map<String, Object>> ordersByDay = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DATE(created_at) as day, COUNT(*) as count, COALESCE(SUM(total),0) as revenue "
                + "FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) "
                + "GROUP BY DATE(created_at) ORDER BY day")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("day", rs.getString("day"));
                        row.put("count", rs.getInt("count"));
                        row.put("revenue", rs.getInt("revenue"));
                        ordersByDay.add(row);
                    }
                }
            }
            stats.put("ordersByDay", ordersByDay);

            // Orders by month
            List<Map<String, Object>> ordersByMonth = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DATE_FORMAT(created_at,'%Y-%m') as month, COUNT(*) as count, COALESCE(SUM(total),0) as revenue "
                + "FROM orders GROUP BY DATE_FORMAT(created_at,'%Y-%m') ORDER BY month DESC LIMIT 12")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("month", rs.getString("month"));
                        row.put("count", rs.getInt("count"));
                        row.put("revenue", rs.getInt("revenue"));
                        ordersByMonth.add(row);
                    }
                }
            }
            stats.put("ordersByMonth", ordersByMonth);

            // Top cities
            List<Map<String, Object>> topCities = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT city, COUNT(*) as count FROM orders GROUP BY city ORDER BY count DESC LIMIT 10")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("city", rs.getString("city"));
                        row.put("count", rs.getInt("count"));
                        topCities.add(row);
                    }
                }
            }
            stats.put("topCities", topCities);

            // Top categories
            List<Map<String, Object>> topCategories = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.name, COUNT(DISTINCT p.id) as product_count "
                + "FROM categories c LEFT JOIN products p ON c.id = p.category_id "
                + "GROUP BY c.id ORDER BY product_count DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("productCount", rs.getInt("product_count"));
                        topCategories.add(row);
                    }
                }
            }
            stats.put("topCategories", topCategories);

            // Revenue by supplier
            List<Map<String, Object>> revenueBySupplier = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.name, COALESCE(SUM(oi.unit_price * oi.qty),0) as revenue "
                + "FROM suppliers s LEFT JOIN products p ON s.id = p.supplier_id "
                + "LEFT JOIN order_items oi ON p.id = oi.product_id "
                + "LEFT JOIN orders o ON oi.order_id = o.id AND o.status != 'ANNULEE' "
                + "GROUP BY s.id ORDER BY revenue DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("revenue", rs.getInt("revenue"));
                        revenueBySupplier.add(row);
                    }
                }
            }
            stats.put("revenueBySupplier", revenueBySupplier);

            // Cancellation rate
            int totalOrders = count(conn, "SELECT COUNT(*) FROM orders");
            int cancelledOrders = count(conn, "SELECT COUNT(*) FROM orders WHERE status='ANNULEE'");
            stats.put("cancellationRate", totalOrders > 0 ? Math.round((double)cancelledOrders / totalOrders * 100) : 0);

            // Average cart
            stats.put("averageCart", count(conn, "SELECT COALESCE(ROUND(AVG(total)),0) FROM orders WHERE status != 'ANNULEE'"));

            // Low rotation products (products with 0 sales)
            stats.put("lowRotationProducts", count(conn,
                "SELECT COUNT(*) FROM products p WHERE p.id NOT IN (SELECT DISTINCT product_id FROM order_items)"));

            // Payment stats
            stats.put("totalPaymentRevenue", count(conn, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='REUSSIE'"));
            List<Map<String, Object>> paymentsByMethod = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT method, COUNT(*) as count, COALESCE(SUM(amount),0) as total FROM payments GROUP BY method")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("method", rs.getString("method"));
                        row.put("count", rs.getInt("count"));
                        row.put("total", rs.getInt("total"));
                        paymentsByMethod.add(row);
                    }
                }
            }
            stats.put("paymentsByMethod", paymentsByMethod);
        }
        return stats;
    }

    private int count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }
}
