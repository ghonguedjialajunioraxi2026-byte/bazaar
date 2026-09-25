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
        return req.getParameter("token");
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

            // Default: dashboard
            resp.getWriter().write(JsonUtil.toJson(getDashboard()));
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
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
                } else {
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
                        if (approved) {
                            notificationDAO.createForSupplier(supplierId, "Produit validé",
                                "Votre produit '" + productName + "' est maintenant en ligne sur la boutique.", "SUPPLIER");
                        } else {
                            notificationDAO.createForSupplier(supplierId, "Produit refusé",
                                "Votre produit '" + productName + "' a été refusé par l'administrateur.", "SUPPLIER");
                        }
                    }
                } finally {
                    if (conn != null) conn.close();
                }
                activityLogDAO.log(adminId, approved ? "PRODUCT_APPROVED" : "PRODUCT_REJECTED", "product", productId,
                    "Produit '" + productName + "' " + (approved ? "approuvé" : "refusé"), req.getRemoteAddr());

                resp.getWriter().write("{\"success\":true,\"status\":\"" + (approved ? "APPROVED" : "REJECTED") + "\"}");
                return;
            }

            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Action requise\"}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
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
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private Map<String, Object> getDashboard() throws SQLException {
        Map<String, Object> dash = new LinkedHashMap<>();
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
