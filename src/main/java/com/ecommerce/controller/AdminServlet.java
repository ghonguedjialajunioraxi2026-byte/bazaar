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
                limit = com.ecommerce.util.Pagination.clampLimit(limit, 200);
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

            // Demandes de catégories fournisseurs (file de validation).
            if ("/category-requests".equals(pathInfo)) {
                String status = req.getParameter("status");
                if (status == null || status.trim().isEmpty()) status = "PENDING";
                int limit = boundedLimit(req, 50);
                resp.getWriter().write(JsonUtil.toJson(
                    new com.ecommerce.dao.CategoryRequestDAO().findByStatus(status, limit)));
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
                    req.getParameter("status"), boundedPage(req), boundedLimit(req, 20))));
                return;
            }
            if ("/shipments".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.shipments(
                    req.getParameter("filter"), boundedLimit(req, 50))));
                return;
            }
            // Finance BAZAAR : payouts, refunds, litiges, journal, paramètres.
            if ("/payouts".equals(pathInfo)) {
                int page = boundedPage(req);
                int limit = boundedLimit(req, 20);
                resp.getWriter().write(JsonUtil.toJson(
                    new com.ecommerce.dao.PayoutDAO().findPagedWithOrder(
                        req.getParameter("status"), limit, (page - 1) * limit)));
                return;
            }
            if ("/refunds".equals(pathInfo)) {
                int page = boundedPage(req);
                int limit = boundedLimit(req, 20);
                java.util.List<com.ecommerce.model.Refund> rows =
                    new com.ecommerce.dao.RefundDAO().findPaged(
                        req.getParameter("status"), limit, (page - 1) * limit);
                java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
                com.ecommerce.dao.OrderDAO odao = new com.ecommerce.dao.OrderDAO();
                for (com.ecommerce.model.Refund r : rows) {
                    Map<String, Object> m = new LinkedHashMap<>(
                        com.ecommerce.pawapay.PawaPayRefundService.maskedView(r));
                    try {
                        com.ecommerce.model.Order o = odao.findById(r.getOrderId());
                        m.put("orderNumber", o == null ? null : o.getOrderNumber());
                    } catch (Exception ignored) {}
                    out.add(m);
                }
                resp.getWriter().write(JsonUtil.toJson(out));
                return;
            }
            if ("/disputes".equals(pathInfo)) {
                int page = boundedPage(req);
                int limit = boundedLimit(req, 20);
                java.util.List<com.ecommerce.model.Dispute> rows =
                    new com.ecommerce.dao.DisputeDAO().findPaged(
                        req.getParameter("status"), limit, (page - 1) * limit);
                java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
                com.ecommerce.dao.OrderDAO odao = new com.ecommerce.dao.OrderDAO();
                for (com.ecommerce.model.Dispute d : rows) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("orderId", d.getOrderId());
                    m.put("phone", d.getPhone());
                    m.put("reason", d.getReason());
                    m.put("status", d.getStatus());
                    m.put("resolutionNote", d.getResolutionNote());
                    m.put("resolvedAt", d.getResolvedAt());
                    m.put("createdAt", d.getCreatedAt());
                    try {
                        com.ecommerce.model.Order o = odao.findById(d.getOrderId());
                        m.put("orderNumber", o == null ? null : o.getOrderNumber());
                        m.put("orderTotal", o == null ? null : o.getTotal());
                        m.put("orderStatus", o == null ? null : o.getStatus());
                    } catch (Exception ignored) {}
                    out.add(m);
                }
                resp.getWriter().write(JsonUtil.toJson(out));
                return;
            }
            if ("/finance/journal".equals(pathInfo)) {
                int orderId = parseInt(req.getParameter("orderId"), 0);
                if (orderId <= 0) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"orderId requis\"}");
                    return;
                }
                resp.getWriter().write(JsonUtil.toJson(
                    new com.ecommerce.service.FinanceJournalService().forOrder(orderId)));
                return;
            }
            if ("/finance/settings".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(
                    new com.ecommerce.dao.PlatformSettingsDAO().findAll()));
                return;
            }
            // --- PawaPay Finances + Toolkit (diagnostic admin, lecture seule
            // sauf initiation statement = opération PSP = MONEY) ---
            com.ecommerce.pawapay.PawaPayToolkitService toolkit =
                new com.ecommerce.pawapay.PawaPayToolkitService();
            if ("/pawapay/wallet".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        walletView(req.getParameter("country"))));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/pawapay/statements".equals(pathInfo)) {
                int limit = boundedLimit(req, 20);
                java.util.List<com.ecommerce.model.PawaPayStatement> rows =
                    new com.ecommerce.pawapay.PawaPayStatementService().recent(limit);
                java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
                for (com.ecommerce.model.PawaPayStatement s : rows) {
                    out.add(com.ecommerce.pawapay.PawaPayStatementService.maskedView(s));
                }
                resp.getWriter().write(JsonUtil.toJson(out));
                return;
            }
            if ("/pawapay/statements/status".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        new com.ecommerce.pawapay.PawaPayStatementService()
                            .getStatementStatus(req.getParameter("statementId"))));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/pawapay/configuration".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(toolkit.activeConfiguration(
                        req.getParameter("country"), req.getParameter("operationType"))));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/pawapay/availability".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(toolkit.availability(
                        req.getParameter("country"), req.getParameter("operationType"))));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/pawapay/public-keys".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(toolkit.publicKeys()));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/pawapay/reconciliation".equals(pathInfo)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(reconciliationView()));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/conversations".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.conversations(
                    boundedPage(req), boundedLimit(req, 20))));
                return;
            }
            if ("/offers/pending".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.pendingOffers(
                    boundedPage(req), boundedLimit(req, 20))));
                return;
            }
            if ("/reviews".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.reviews(req.getParameter("status"))));
                return;
            }
            if ("/audit".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.audit(
                    req.getParameter("action"), boundedPage(req),
                    boundedLimit(req, 20))));
                return;
            }

            // Tableau de bord SYNCHRONISÉ : admin + client + fournisseur,
            // UN seul appel, UN seul instant T (anti-dérive entre onglets).
            if ("/dashboard/synced".equals(pathInfo)) {
                resp.getWriter().write(JsonUtil.toJson(control.syncedDashboard()));
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

                int adminId0 = validateAndGetAdminId(req);
                if (adminId0 <= 0) { resp.setStatus(401); resp.getWriter().write("{\"error\":\"Non autorisé\"}"); return; }

                // Validation d'une demande de catégorie fournisseur.
                if (body.get("categoryRequestId") != null) {
                    int reqId = body.get("categoryRequestId") instanceof Number
                        ? ((Number) body.get("categoryRequestId")).intValue() : 0;
                    boolean approved = body.get("approved") == null
                        || Boolean.parseBoolean(body.get("approved").toString());
                    String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
                    if (reqId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"Demande requise\"}"); return; }
                    try {
                        new com.ecommerce.service.CategoryService()
                            .resolveCategoryRequest(reqId, approved, note);
                        activityLogDAO.log(adminId0,
                            approved ? "CATEGORY_REQUEST_APPROVED" : "CATEGORY_REQUEST_REJECTED",
                            "category_request", reqId, approved ? "Demande acceptée" : "Demande refusée",
                            req.getRemoteAddr());
                        resp.getWriter().write("{\"success\":true}");
                    } catch (Exception ex) {
                        String msg = ex.getMessage() == null ? "Traitement impossible"
                            : ex.getMessage().replace("\\", "\\\\").replace("\"", "\\\"");
                        resp.setStatus(409);
                        resp.getWriter().write("{\"error\":\"" + msg + "\"}");
                    }
                    return;
                }

                int adminId = adminId0;

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

            if ("/monetization/rules/activate".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
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

            // --- Finance BAZAAR : payout / refund / litiges / réception / paramètres ---
            // Déclenche (ou réessaie) le versement PawaPay d'une commande (MONEY).
            if ("/payouts/process".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    com.ecommerce.pawapay.PawaPayPayoutService.PayoutOutcome outcome =
                        new com.ecommerce.pawapay.PawaPayPayoutService().initiateOrRetry(orderId);
                    activityLogDAO.log(adminId, "PAYOUT_INITIATED", "payout", outcome.payout.getId(),
                        "Commande " + orderId + " (" + outcome.payout.getStatus() + ")", req.getRemoteAddr());
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("duplicate", outcome.duplicate);
                    out.put("payout", com.ecommerce.pawapay.PawaPayPayoutService.maskedView(outcome.payout));
                    resp.setStatus(outcome.duplicate ? 200 : 201);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Déclenche (ou réessaie) le remboursement PawaPay d'une commande (MONEY).
            if ("/refunds/request".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    com.ecommerce.pawapay.PawaPayRefundService.RefundOutcome outcome =
                        new com.ecommerce.pawapay.PawaPayRefundService().initiateOrRetry(orderId);
                    activityLogDAO.log(adminId, "REFUND_INITIATED", "refund", outcome.refund.getId(),
                        "Commande " + orderId + " (" + outcome.refund.getStatus() + ")", req.getRemoteAddr());
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("duplicate", outcome.duplicate);
                    out.put("refund", com.ecommerce.pawapay.PawaPayRefundService.maskedView(outcome.refund));
                    resp.setStatus(outcome.duplicate ? 200 : 201);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Annule un remboursement ENQUEUED côté PawaPay (MONEY).
            // Garde : refusé si non annulable (COMPLETED/FAILED/PROCESSING...).
            if ("/refunds/cancel".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    Map<String, Object> out =
                        new com.ecommerce.pawapay.PawaPayRefundService().cancelEnqueued(orderId);
                    activityLogDAO.log(adminId, "REFUND_CANCELLED", "refund", orderId,
                        "Annulation ENQUEUED acceptée", req.getRemoteAddr());
                    out.put("success", true);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Résolution d'un litige : REFUND (rembourse) ou REJECT (rejette) (MONEY).
            if ("/disputes/resolve".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                String decision = body.get("decision") == null ? null : String.valueOf(body.get("decision"));
                String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    Map<String, Object> out =
                        new com.ecommerce.service.DisputeService().resolve(orderId, decision, note);
                    activityLogDAO.log(adminId, "DISPUTE_RESOLVED", "dispute", orderId,
                        String.valueOf(decision), req.getRemoteAddr());
                    out.put("success", true);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Confirmation de réception admin (déclenche le payout si éligible) (MONEY).
            if ("/shipments/confirm".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    com.ecommerce.model.Shipment s =
                        new com.ecommerce.service.ShipmentService().confirmReceiptByAdmin(orderId);
                    activityLogDAO.log(adminId, "RECEIPT_CONFIRMED", "shipment", s.getId(),
                        "Commande " + orderId, req.getRemoteAddr());
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("receiptConfirmed", s.isReceiptConfirmed());
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Paramètre financier (MONEY) : seul pawapay.payout.fee est modifiable ici.
            if ("/finance/settings".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                String key = body.get("key") == null ? "" : String.valueOf(body.get("key"));
                if ("pawapay.payout.fee".equals(key)) {
                    int value = body.get("value") instanceof Number
                        ? ((Number) body.get("value")).intValue() : -1;
                    if (value < 0) {
                        resp.setStatus(400);
                        resp.getWriter().write("{\"error\":\"Paramètre invalide\"}");
                        return;
                    }
                    try {
                        new com.ecommerce.dao.PlatformSettingsDAO().set(key, String.valueOf(value));
                    } catch (java.sql.SQLException e) {
                        throw new com.ecommerce.util.AppException(
                            com.ecommerce.util.ErrorCodes.DATABASE_ERROR, 500,
                            "Impossible de traiter votre demande.", e);
                    }
                    activityLogDAO.log(adminId, "FINANCE_SETTING_UPDATED", "platform_setting", 0,
                        key + "=" + value, req.getRemoteAddr());
                    resp.getWriter().write("{\"success\":true}");
                    return;
                }
                // URL publique HTTPS recevant les callbacks statements PawaPay
                // (exigée par POST /v2/statements : INVALID_CALLBACK_URL sinon).
                if ("pawapay.statement.callback_url".equals(key)) {
                    String value = body.get("value") == null ? ""
                        : String.valueOf(body.get("value")).trim();
                    if (!value.toLowerCase().startsWith("https://") || value.length() > 500) {
                        resp.setStatus(400);
                        resp.getWriter().write("{\"error\":\"URL HTTPS valide requise\"}");
                        return;
                    }
                    try {
                        new com.ecommerce.dao.PlatformSettingsDAO().set(key, value);
                    } catch (java.sql.SQLException e) {
                        throw new com.ecommerce.util.AppException(
                            com.ecommerce.util.ErrorCodes.DATABASE_ERROR, 500,
                            "Impossible de traiter votre demande.", e);
                    }
                    activityLogDAO.log(adminId, "FINANCE_SETTING_UPDATED", "platform_setting", 0,
                        key + " configurée", req.getRemoteAddr());
                    resp.getWriter().write("{\"success\":true}");
                    return;
                }
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Paramètre invalide\"}");
                return;
            }

            // Annule un versement ENQUEUED côté PawaPay (MONEY).
            // Garde : refusé si non annulable (COMPLETED/FAILED/PROCESSING...).
            if ("/payouts/cancel".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                int orderId = body.get("orderId") instanceof Number
                    ? ((Number) body.get("orderId")).intValue() : 0;
                if (orderId <= 0) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderId requis\"}"); return; }
                try {
                    Map<String, Object> out =
                        new com.ecommerce.pawapay.PawaPayPayoutService().cancelEnqueued(orderId);
                    activityLogDAO.log(adminId, "PAYOUT_CANCELLED", "payout", orderId,
                        "Annulation ENQUEUED acceptée", req.getRemoteAddr());
                    out.put("success", true);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Versements groupés admin (MONEY) : UN appel PawaPay bulk (max 20).
            // Complément du payout individuel (N fournisseurs en un traitement).
            if ("/payouts/bulk".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                java.util.List<Integer> orderIds = new java.util.ArrayList<>();
                Object raw = body.get("orderIds");
                if (raw instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) raw) {
                        if (o instanceof Number) orderIds.add(((Number) o).intValue());
                    }
                } else if (body.get("orderId") instanceof Number) {
                    orderIds.add(((Number) body.get("orderId")).intValue());
                }
                if (orderIds.isEmpty()) { resp.setStatus(400); resp.getWriter().write("{\"error\":\"orderIds requis\"}"); return; }
                try {
                    java.util.List<com.ecommerce.pawapay.PawaPayPayoutService.BulkItemResult> results =
                        new com.ecommerce.pawapay.PawaPayPayoutService().initiateBulk(orderIds);
                    java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
                    int ok = 0;
                    for (com.ecommerce.pawapay.PawaPayPayoutService.BulkItemResult r : results) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("orderId", r.orderId);
                        m.put("orderNumber", r.orderNumber);
                        m.put("payoutId", r.payoutId);
                        m.put("status", r.status);
                        m.put("duplicate", r.duplicate);
                        m.put("error", r.error);
                        if (r.error == null
                                && ("ACCEPTED".equals(r.status) || "PROCESSING".equals(r.status)
                                    || "ENQUEUED".equals(r.status))) ok++;
                        items.add(m);
                    }
                    activityLogDAO.log(adminId, "PAYOUT_BULK_INITIATED", "payout", 0,
                        ok + "/" + items.size() + " acceptés", req.getRemoteAddr());
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("accepted", ok);
                    out.put("total", items.size());
                    out.put("items", items);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Demande de relevé PawaPay (MONEY : opération PSP, même sans
            // mouvement d'argent). Plage ≤ 31 jours, callback HTTPS configurée.
            if ("/pawapay/statements".equals(pathInfo)) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;

                try {
                    Object comp = body.get("compressed");
                    boolean compressed = Boolean.TRUE.equals(comp)
                        || "true".equalsIgnoreCase(String.valueOf(comp));
                    com.ecommerce.model.PawaPayStatement s =
                        new com.ecommerce.pawapay.PawaPayStatementService().initiate(
                            str(body.get("country")), str(body.get("currency")),
                            str(body.get("provider")), str(body.get("startDate")),
                            str(body.get("endDate")), compressed, adminId);
                    activityLogDAO.log(adminId, "STATEMENT_REQUESTED", "statement", s.getId(),
                        s.getWalletCountry() + "/" + s.getWalletCurrency()
                            + " " + s.getStartDate() + "→" + s.getEndDate(),
                        req.getRemoteAddr());
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("statement",
                        com.ecommerce.pawapay.PawaPayStatementService.maskedView(s));
                    resp.setStatus(201);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Prédiction provider (diagnostic admin, lecture seule).
            if ("/pawapay/predict".equals(pathInfo)) {
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        new com.ecommerce.pawapay.PawaPayToolkitService()
                            .predict(str(body.get("phoneNumber")))));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
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
            // Jamais d'invalidation sans session valide : sinon n'importe qui
            // peut déconnecter un admin (DoS) et sonder la validité d'un token.
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Bornes serveur (Phase 20) : page/limit hostiles => 500 (OFFSET négatif). */
    private int boundedPage(jakarta.servlet.http.HttpServletRequest req) {
        return com.ecommerce.util.Pagination.clampPage(parseInt(req.getParameter("page"), 1));
    }

    private int boundedLimit(jakarta.servlet.http.HttpServletRequest req, int def) {
        return com.ecommerce.util.Pagination.clampLimit(parseInt(req.getParameter("limit"), def), 100);
    }

    /**
     * Soldes wallets PawaPay (diagnostic admin, lecture seule).
     * Information DISTANTE horodatée — jamais fusionnée à la comptabilité
     * interne (le journal BAZAAR reste la source comptable).
     */
    private Map<String, Object> walletView(String country)
            throws com.ecommerce.util.AppException {
        String c = (country == null || country.trim().isEmpty()) ? null
            : com.ecommerce.pawapay.PawaPayToolkitService.requireCountry(country);
        com.ecommerce.pawapay.PawaPayConfig.requireConfigured();
        Map<String, Object> remote;
        try {
            remote = new com.ecommerce.pawapay.PawaPayClient().getWalletBalances(c);
        } catch (com.ecommerce.util.AppException e) {
            throw e;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt",
            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        out.put("environment", com.ecommerce.pawapay.PawaPayConfig.getEnvironment().toUpperCase());
        out.put("balances", remote == null ? new java.util.ArrayList<>() : remote.get("balances"));
        out.put("note", "Solde distant PawaPay (diagnostic) — distinct du journal"
            + " comptable BAZAAR, aucune écriture n'en découle.");
        return out;
    }

    /**
     * Vue réconciliation PawaPay vs BAZAAR (diagnostic, lecture seule).
     * Affiche côte à côte SANS fusion : tout écart = ALERTE à instruire,
     * jamais de correction silencieuse.
     */
    private Map<String, Object> reconciliationView()
            throws com.ecommerce.util.AppException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt",
            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        // Distant : wallet (best-effort, jamais bloquant).
        try {
            out.put("pawapayWallet", walletView(null));
        } catch (Exception e) {
            out.put("pawapayWallet", null);
            out.put("pawapayWalletError", "Prestataire injoignable");
        }
        // Local : compteurs par statut + ledger.
        try {
            Map<String, Object> local = new LinkedHashMap<>();
            com.ecommerce.dao.PaymentDAO payDAO = new com.ecommerce.dao.PaymentDAO();
            Map<String, Integer> deposits = new LinkedHashMap<>();
            for (String st : new String[]{"PENDING", "PROCESSING", "COMPLETED", "FAILED"}) {
                deposits.put(st, payDAO.countByStatus(st));
            }
            local.put("deposits", deposits);
            local.put("payouts", new com.ecommerce.dao.PayoutDAO().countByStatus());
            local.put("refunds", new com.ecommerce.dao.RefundDAO().countByStatus());
            local.put("statements",
                new com.ecommerce.dao.StatementDAO().countByStatus());
            local.put("ledger", new com.ecommerce.service.MonetizationService().platformOverview());
            out.put("bazaar", local);
        } catch (Exception e) {
            throw new com.ecommerce.util.AppException(
                com.ecommerce.util.ErrorCodes.DATABASE_ERROR, 500,
                "Impossible de traiter votre demande.", e);
        }
        out.put("note", "Comparaison indicative : le wallet PawaPay (portefeuille"
            + " PSP) et le ledger BAZAAR (comptabilité interne) sont deux concepts"
            + " différents. Tout écart constaté doit être instruit, jamais"
            + " corrigé automatiquement.");
        return out;
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
            dash.put("todayOrders", count(conn, "SELECT COUNT(*) FROM orders WHERE created_at >= CURDATE() AND created_at < CURDATE() + INTERVAL 1 DAY"));
            dash.put("todayRevenue", count(conn, "SELECT COALESCE(SUM(total),0) FROM orders WHERE created_at >= CURDATE() AND created_at < CURDATE() + INTERVAL 1 DAY AND status != 'ANNULEE'"));
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
