package com.ecommerce.controller;

import com.ecommerce.model.*;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.service.SupplierService;
import com.ecommerce.util.JsonUtil;
import com.ecommerce.util.UploadUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

@WebServlet("/api/supplier/*")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 10 * 1024 * 1024,
    maxRequestSize = 30 * 1024 * 1024
)
public class SupplierApiServlet extends HttpServlet {
    private final SupplierAuthService authService = new SupplierAuthService();
    private final SupplierService supplierService = new SupplierService();

    private String extractToken(HttpServletRequest req) {
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) return token.substring(7);
        // Header seul : le token en query (?token=) fuit dans logs/historique/Referer.
        return null;
    }

    private int requireAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int supplierId = 0;
        try {
            supplierId = authService.validateToken(extractToken(req));
        } catch (Exception ignored) {}
        if (supplierId <= 0) {
            resp.setStatus(401);
            resp.getWriter().write("{\"success\":false,\"message\":\"Session expirée\"}");
        }
        return supplierId;
    }

    private void write(HttpServletResponse resp, Object data, int status) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(JsonUtil.toJson(data));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            int supplierId = requireAuth(req, resp);
            if (supplierId <= 0) return;

            // /api/supplier/me
            if (path.equals("/me")) {
                Supplier s = supplierService.getProfile(supplierId);
                write(resp, ok("Profil récupéré", safeSupplier(s)), 200);
                return;
            }
            // /api/supplier/logout
            if (path.equals("/logout")) {
                authService.logout(extractToken(req));
                write(resp, ok("Déconnexion réussie", null), 200);
                return;
            }
            // /api/supplier/dashboard
            if (path.equals("/dashboard")) {
                write(resp, ok("Dashboard", supplierService.getDashboard(supplierId)), 200);
                return;
            }
            // /api/supplier/statistics
            if (path.equals("/statistics")) {
                write(resp, ok("Statistiques", supplierService.getStatistics(supplierId)), 200);
                return;
            }
            // /api/supplier/revenue
            if (path.equals("/revenue")) {
                write(resp, ok("Revenus", supplierService.getRevenue(supplierId)), 200);
                return;
            }
            // /api/supplier/products[/{id}]
            if (path.equals("/products") || path.startsWith("/products/")) {
                String sub = path.substring("/products".length());
                if (sub.isEmpty()) {
                    write(resp, ok("Produits", supplierService.getProductsBySupplier(supplierId)), 200);
                } else {
                    int pid = parseInt(sub.substring(1));
                    Product p = supplierService.getProductForSupplier(supplierId, pid);
                    if (p == null) { write(resp, err("Produit introuvable"), 404); return; }
                    write(resp, ok("Produit", p), 200);
                }
                return;
            }
            // /api/supplier/orders[/{id}]
            if (path.equals("/orders") || path.startsWith("/orders/")) {
                String sub = path.substring("/orders".length());
                if (sub.isEmpty()) {
                    write(resp, ok("Commandes", supplierService.getOrdersEnriched(supplierId)), 200);
                } else if (sub.endsWith("/finance")) {
                    int oid = parseInt(sub.substring(1, sub.length() - "/finance".length()));
                    java.util.Map<String, Object> fin = supplierService.getOrderFinance(supplierId, oid);
                    if (fin == null) { write(resp, err("Commande introuvable"), 404); return; }
                    write(resp, ok("Finance commande", fin), 200);
                } else {
                    int oid = parseInt(sub.substring(1));
                    Order o = supplierService.getOrderForSupplier(supplierId, oid);
                    if (o == null) { write(resp, err("Commande introuvable"), 404); return; }
                    write(resp, ok("Commande", o), 200);
                }
                return;
            }
            // /api/supplier/shipments/by-id/{shipmentId} -> {orderId, orderNumber}
            // Bouton « VOIR LA COMMANDE » des notifications (entité SHIPMENT).
            // Propriété vérifiée côté service (404/403 hors périmètre).
            if (path.startsWith("/shipments/by-id/")) {
                int sid = parseInt(path.substring("/shipments/by-id/".length()));
                if (sid <= 0) { write(resp, err("Expédition introuvable"), 404); return; }
                java.util.Map<String, Object> data =
                    new com.ecommerce.service.ShipmentService().shipmentOrderForSupplier(sid, supplierId);
                write(resp, ok("Commande", data), 200);
                return;
            }
            // /api/supplier/conversations
            if (path.equals("/conversations")) {
                write(resp, ok("Conversations", supplierService.getConversations(supplierId)), 200);
                return;
            }
            // /api/supplier/conversations/{id}
            if (path.startsWith("/conversations/") && !path.endsWith("/messages")) {
                int cid = parseInt(path.substring("/conversations/".length()));
                Conversation c = supplierService.getConversationForSupplier(supplierId, cid);
                if (c == null) { write(resp, err("Conversation introuvable"), 404); return; }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("conversation", c);
                data.put("messages", supplierService.getMessagesForSupplier(supplierId, cid));
                try {
                    com.ecommerce.dao.ConversationDAO cdao = new com.ecommerce.dao.ConversationDAO();
                    if (c.getProductId() != null) {
                        java.util.Map<String, Object> p = cdao.productSnippet(c.getProductId());
                        if (p != null) data.put("product", p);
                    }
                    if (c.getOrderId() != null) {
                        String num = cdao.findOrderNumber(c.getOrderId());
                        if (num != null) data.put("orderNumber", num);
                    }
                } catch (Exception ignored) {}
                try {
                    data.put("offers", new com.ecommerce.service.OfferService().listForConversation(cid));
                } catch (Exception ignored) {
                    data.put("offers", new java.util.ArrayList<>());
                }
                write(resp, ok("Conversation", data), 200);
                return;
            }
            // /api/supplier/notifications (centre paginé + filtres)
            // archived=true -> sauvegardées, archived=all -> toutes, défaut -> boîte active.
            if (path.equals("/notifications")) {
                String category = req.getParameter("type");
                if (category == null) category = req.getParameter("category");
                String status = req.getParameter("status");
                Boolean unreadOnly = null;
                if ("unread".equalsIgnoreCase(status)) unreadOnly = true;
                else if ("read".equalsIgnoreCase(status)) unreadOnly = false;
                Boolean archivedOnly = false;
                String arch = req.getParameter("archived");
                if ("all".equalsIgnoreCase(arch)) archivedOnly = null;
                else if ("true".equalsIgnoreCase(arch) || "1".equals(arch)) archivedOnly = true;
                int page = parseNum(req.getParameter("page"), 1);
                int limit = Math.min(50, Math.max(1, parseNum(req.getParameter("limit"), 20)));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("list", supplierService.getNotificationsPaged(supplierId, category, unreadOnly, archivedOnly, limit, (page - 1) * limit));
                data.put("total", supplierService.countNotifications(supplierId, category, unreadOnly, archivedOnly));
                data.put("unread", supplierService.getUnreadNotifications(supplierId));
                data.put("page", page);
                data.put("limit", limit);
                write(resp, ok("Notifications", data), 200);
                return;
            }
            // /api/supplier/notifications/unread-count (polling léger)
            if (path.equals("/notifications/unread-count")) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", supplierService.getUnreadNotifications(supplierId));
                write(resp, ok("Compteur", data), 200);
                return;
            }
            // /api/supplier/notifications/preferences
            if (path.equals("/notifications/preferences")) {
                write(resp, ok("Préférences",
                    new com.ecommerce.dao.NotificationDAO().getPreferences("SUPPLIER", supplierId)), 200);
                return;
            }
            // /api/supplier/payments
            if (path.equals("/payments")) {
                write(resp, ok("Paiements", supplierService.getPayments(supplierId)), 200);
                return;
            }
            // /api/supplier/category-requests — mes demandes (statuts visibles).
            if (path.equals("/category-requests")) {
                write(resp, ok("Demandes de catégories",
                    supplierService.getCategoryRequests(supplierId)), 200);
                return;
            }
            // /api/supplier/offers — négociations en attente (toutes conversations)
            if (path.equals("/offers")) {
                write(resp, ok("Offres en attente",
                    new com.ecommerce.dao.OfferDAO().findPendingBySupplier(supplierId)), 200);
                return;
            }
            // /api/supplier/ledger — écritures comptables (limite 50, bornée Phase 20)
            if (path.equals("/ledger")) {
                int limit = 50;
                try { limit = parseInt(req.getParameter("limit")); } catch (Exception ignored) {}
                limit = com.ecommerce.util.Pagination.clampLimit(limit, 50);
                write(resp, ok("Grand-livre",
                    new com.ecommerce.service.MonetizationService().entriesForSupplier(supplierId, limit)), 200);
                return;
            }
            // /api/supplier/profile
            if (path.equals("/profile")) {
                write(resp, ok("Profil", safeSupplier(supplierService.getProfile(supplierId))), 200);
                return;
            }
            // badges
            if (path.equals("/badges")) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("notifications", supplierService.getUnreadNotifications(supplierId));
                data.put("messages", supplierService.unreadConversations(supplierId));
                data.put("orders", supplierService.getDashboard(supplierId).get("pendingOrders"));
                write(resp, ok("Badges", data), 200);
                return;
            }

            write(resp, err("Route inconnue"), 404);
        } catch (Exception e) {
            write(resp, err("Impossible de traiter votre demande."), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            int supplierId = requireAuth(req, resp);
            if (supplierId <= 0) return;

            // POST products (avec ou sans upload image)
            if (path.equals("/products")) {
                String contentType = req.getContentType();
                Product p = new Product();
                List<String> images = new ArrayList<>();
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    p.setName(req.getParameter("name"));
                    p.setBrand(req.getParameter("brand"));
                    p.setReference(req.getParameter("reference"));
                    p.setDescription(req.getParameter("description"));
                    p.setCategoryId(parseInt(req.getParameter("categoryId")));
                    p.setPrice(parseInt(req.getParameter("price")));
                    p.setOldPrice(optInt(req.getParameter("oldPrice")));
                    p.setDiscount(parseInt(req.getParameter("discount")));
                    p.setStock(parseInt(req.getParameter("stock")));
                    p.setDeliveryPrice(parseInt(req.getParameter("deliveryPrice")));
                    p.setSizes(parseJsonList(req.getParameter("sizes")));
                    p.setColors(parseJsonList(req.getParameter("colors")));
                    String existingImages = req.getParameter("existingImages");
                    if (existingImages != null && !existingImages.isEmpty()) images.addAll(parseJsonList(existingImages));
                    images.addAll(saveParts(req));
                } else {
                    BufferedReader reader = req.getReader();
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                    p.setName(str(body.get("name")));
                    p.setBrand(str(body.get("brand")));
                    p.setReference(str(body.get("reference")));
                    p.setDescription(str(body.get("description")));
                    p.setCategoryId(num(body.get("categoryId")));
                    p.setPrice(num(body.get("price")));
                    p.setOldPrice(body.get("oldPrice") == null ? null : num(body.get("oldPrice")));
                    p.setDiscount(num(body.get("discount")));
                    p.setStock(num(body.get("stock")));
                    p.setDeliveryPrice(num(body.get("deliveryPrice")));
                    p.setSizes(stringList(body.get("sizes")));
                    p.setColors(stringList(body.get("colors")));
                    images = stringList(body.get("images"));
                }
                if (p.getName() == null || p.getName().isEmpty() || p.getPrice() <= 0) {
                    write(resp, err("Nom et prix sont obligatoires"), 400); return;
                }
                p.setImages(images);
                int id = supplierService.createProductForSupplier(supplierId, p);
                write(resp, ok("Produit ajouté à la boutique", id), 201);
                return;
            }

            // POST conversations/{id}/messages
            if (path.startsWith("/conversations/") && path.endsWith("/messages")) {
                int cid = parseInt(path.substring("/conversations/".length(), path.indexOf("/messages")));
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                boolean sent = supplierService.sendSupplierMessage(supplierId, cid, str(body.get("body")));
                if (!sent) { write(resp, err("Conversation introuvable"), 404); return; }
                write(resp, ok("Message envoyé", null), 201);
                return;
            }

            // POST category-requests : proposer une catégorie / sous-catégorie
            // (validation admin requise, jamais de création directe).
            if (path.equals("/category-requests")) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                String name = str(body.get("name"));
                Object rawParent = body.get("parentId") != null ? body.get("parentId") : body.get("parent_id");
                Integer parentId = null;
                if (rawParent instanceof Number) {
                    int v = ((Number) rawParent).intValue();
                    if (v > 0) parentId = v;
                }
                try {
                    int id = supplierService.requestCategory(supplierId, name, parentId);
                    write(resp, ok("Demande envoyée, en attente de validation", id), 201);
                } catch (com.ecommerce.util.AppException ae) {
                    write(resp, err(ae.getMessage()), ae.getHttpStatus() > 0 ? ae.getHttpStatus() : 400);
                }
                return;
            }

            // POST payments/request
            if (path.equals("/payments/request")) {                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                int amount = num(body.get("amount"));
                String method = str(body.get("method"));
                boolean done = supplierService.requestPayment(supplierId, amount, method);
                if (!done) { write(resp, err("Montant indisponible"), 400); return; }
                write(resp, ok("Demande de paiement envoyée", null), 201);
                return;
            }

            // POST logout
            if (path.equals("/logout")) {
                authService.logout(extractToken(req));
                write(resp, ok("Déconnexion réussie", null), 200);
                return;
            }

            // notifications read-all
            if (path.equals("/notifications/read-all")) {
                supplierService.markAllNotificationsRead(supplierId);
                write(resp, ok("Toutes lues", null), 200);
                return;
            }

            // PUT /api/supplier/notifications/preferences {category, enabled}
            if (path.equals("/notifications/preferences")) {
                BufferedReader preader = req.getReader();
                StringBuilder psb = new StringBuilder();
                String pline;
                while ((pline = preader.readLine()) != null) psb.append(pline);
                Map<String, Object> pbody = JsonUtil.fromJson(psb.toString(), Map.class);
                String rawCategory = str(pbody.get("category"));
                Object enObj = pbody.get("enabled");
                boolean enabled = !(enObj instanceof Boolean) || (Boolean) enObj;
                if (rawCategory == null || rawCategory.trim().isEmpty()) {
                    write(resp, err("Catégorie inconnue"), 400); return;
                }
                final String catUp = rawCategory.trim().toUpperCase();
                boolean knownCategory = com.ecommerce.service.NotificationType.all().values().stream()
                        .anyMatch(d -> d.category.equals(catUp));
                if (!knownCategory) { write(resp, err("Catégorie inconnue"), 400); return; }
                String category = catUp;
                if ("SECURITY".equals(category)) { write(resp, err("Les alertes de sécurité ne peuvent pas être désactivées"), 400); return; }
                new com.ecommerce.dao.NotificationDAO().setPreference("SUPPLIER", supplierId, category, enabled);
                write(resp, ok("Préférence enregistrée", null), 200);
                return;
            }

            write(resp, err("Route inconnue"), 404);
        } catch (com.ecommerce.util.AppException ae) {
            write(resp, err(ae.getMessage()), ae.getHttpStatus());
        } catch (java.io.IOException ioe) {
            // Upload refusé (format, contenu, taille) : erreur client, pas 500.
            write(resp, err("Image invalide ou refusée."), 400);
        } catch (Exception e) {
            write(resp, err("Impossible de traiter votre demande."), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            int supplierId = requireAuth(req, resp);
            if (supplierId <= 0) return;

            // PUT products/{id}  (exclut /stock et /toggle)
            if (path.matches("/products/\\d+")) {
                int pid = parseInt(path.substring("/products/".length()));
                String contentType = req.getContentType();
                Product p = new Product();
                List<String> images = new ArrayList<>();
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    p.setName(req.getParameter("name"));
                    p.setBrand(req.getParameter("brand"));
                    p.setReference(req.getParameter("reference"));
                    p.setDescription(req.getParameter("description"));
                    p.setCategoryId(parseInt(req.getParameter("categoryId")));
                    p.setPrice(parseInt(req.getParameter("price")));
                    p.setOldPrice(optInt(req.getParameter("oldPrice")));
                    p.setDiscount(parseInt(req.getParameter("discount")));
                    p.setStock(parseInt(req.getParameter("stock")));
                    p.setDeliveryPrice(parseInt(req.getParameter("deliveryPrice")));
                    p.setSizes(parseJsonList(req.getParameter("sizes")));
                    p.setColors(parseJsonList(req.getParameter("colors")));
                    String existingImages = req.getParameter("existingImages");
                    if (existingImages != null && !existingImages.isEmpty()) images.addAll(parseJsonList(existingImages));
                    images.addAll(saveParts(req));
                } else {
                    BufferedReader reader = req.getReader();
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                    p.setName(str(body.get("name")));
                    p.setBrand(str(body.get("brand")));
                    p.setReference(str(body.get("reference")));
                    p.setDescription(str(body.get("description")));
                    p.setCategoryId(num(body.get("categoryId")));
                    p.setPrice(num(body.get("price")));
                    p.setOldPrice(body.get("oldPrice") == null ? null : num(body.get("oldPrice")));
                    p.setDiscount(num(body.get("discount")));
                    p.setStock(num(body.get("stock")));
                    p.setDeliveryPrice(num(body.get("deliveryPrice")));
                    p.setSizes(stringList(body.get("sizes")));
                    p.setColors(stringList(body.get("colors")));
                    images = stringList(body.get("images"));
                }
                if (p.getName() == null || p.getName().isEmpty() || p.getPrice() <= 0) {
                    write(resp, err("Nom et prix sont obligatoires"), 400); return;
                }
                p.setImages(images);
                boolean ok = supplierService.updateProductForSupplier(supplierId, pid, p);
                if (!ok) { write(resp, err("Produit introuvable"), 404); return; }
                write(resp, ok("Produit mis à jour", null), 200);
                return;
            }

            // PUT products/{id}/stock
            if (path.startsWith("/products/") && path.endsWith("/stock")) {
                int pid = parseInt(path.substring("/products/".length(), path.indexOf("/stock")));
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                int stock = num(body.get("stock"));
                boolean ok = supplierService.setProductStock(supplierId, pid, stock);
                if (!ok) { write(resp, err("Produit introuvable"), 404); return; }
                write(resp, ok("Stock mis à jour", null), 200);
                return;
            }

            // PUT profile
            if (path.equals("/profile")) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                Supplier s = new Supplier();
                s.setName(str(body.get("name")));
                s.setPhone(str(body.get("phone")));
                s.setWhatsapp(str(body.get("whatsapp")));
                s.setEmail(str(body.get("email")));
                s.setContactName(str(body.get("contactName")));
                s.setCity(str(body.get("city")));
                s.setAddress(str(body.get("address")));
                s.setDescription(str(body.get("description")));
                supplierService.updateProfile(supplierId, s);
                write(resp, ok("Profil mis à jour", safeSupplier(supplierService.getProfile(supplierId))), 200);
                return;
            }

            // PUT password
            if (path.equals("/password")) {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                boolean ok = supplierService.changePassword(supplierId, str(body.get("current")), str(body.get("new")));
                if (!ok) { write(resp, err("Mot de passe actuel incorrect"), 400); return; }
                write(resp, ok("Mot de passe modifié. Reconnectez-vous.", null), 200);
                return;
            }

            // PUT logo
            if (path.equals("/logo")) {
                String contentType = req.getContentType();
                String logo = null;
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    List<String> saved = saveParts(req, UploadUtil.SUBDIR_LOGOS);
                    if (!saved.isEmpty()) logo = saved.get(0);
                } else {
                    BufferedReader reader = req.getReader();
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                    logo = str(body.get("logo"));
                }
                if (logo == null || logo.isEmpty()) { write(resp, err("Logo manquant"), 400); return; }
                supplierService.updateLogo(supplierId, logo);
                write(resp, ok("Logo mis à jour", logo), 200);
                return;
            }

            write(resp, err("Route inconnue"), 404);
        } catch (com.ecommerce.util.AppException ae) {
            write(resp, err(ae.getMessage()), ae.getHttpStatus());
        } catch (java.io.IOException ioe) {
            // Upload refusé (format, contenu, taille) : erreur client, pas 500.
            write(resp, err("Image invalide ou refusée."), 400);
        } catch (Exception e) {
            write(resp, err("Impossible de traiter votre demande."), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            int supplierId = requireAuth(req, resp);
            if (supplierId <= 0) return;

            if (path.startsWith("/products/")) {
                int pid = parseInt(path.substring("/products/".length()));
                boolean ok = supplierService.deleteProductForSupplier(supplierId, pid);
                if (!ok) { write(resp, err("Produit introuvable"), 404); return; }
                write(resp, ok("Produit supprimé", null), 200);
                return;
            }
            // DELETE notifications/{id} (propriété vérifiée côté service)
            if (path.startsWith("/notifications/")) {
                int nid = parseInt(path.substring("/notifications/".length()));
                if (nid <= 0) { write(resp, err("Notification introuvable"), 404); return; }
                boolean ok = supplierService.deleteNotification(supplierId, nid);
                if (!ok) { write(resp, err("Notification introuvable"), 404); return; }
                write(resp, ok("Notification supprimée", null), 200);
                return;
            }
            write(resp, err("Route inconnue"), 404);
        } catch (Exception e) {
            write(resp, err("Impossible de traiter votre demande."), 500);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handlePatch(req, resp);
            return;
        }
        super.service(req, resp);
    }

    protected void handlePatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            int supplierId = requireAuth(req, resp);
            if (supplierId <= 0) return;

            // PATCH products/{id}/toggle
            if (path.startsWith("/products/") && path.endsWith("/toggle")) {
                int pid = parseInt(path.substring("/products/".length(), path.indexOf("/toggle")));
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                boolean active = body.get("active") == null || Boolean.TRUE.equals(body.get("active"));
                boolean ok = supplierService.setProductActive(supplierId, pid, active);
                if (!ok) { write(resp, err("Produit introuvable"), 404); return; }
                write(resp, ok("Statut du produit mis à jour", null), 200);
                return;
            }

            // PATCH orders/{id}/status
            if (path.startsWith("/orders/") && path.endsWith("/status")) {
                int oid = parseInt(path.substring("/orders/".length(), path.indexOf("/status")));
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
                String status = str(body.get("status"));
                boolean ok;
                try {
                    ok = supplierService.updateOrderStatusForSupplier(supplierId, oid, status);
                } catch (com.ecommerce.util.AppException ae) {
                    write(resp, err(ae.getMessage()), ae.getHttpStatus());
                    return;
                }
                if (!ok) { write(resp, err("Commande introuvable"), 404); return; }
                write(resp, ok("Statut de la commande mis à jour", null), 200);
                return;
            }

            // PATCH conversations/{id}/read
            if (path.startsWith("/conversations/") && path.endsWith("/read")) {
                int cid = parseInt(path.substring("/conversations/".length(), path.indexOf("/read")));
                boolean ok = supplierService.markConversationRead(supplierId, cid);
                if (!ok) { write(resp, err("Conversation introuvable"), 404); return; }
                write(resp, ok("Conversation marquée comme lue", null), 200);
                return;
            }

            // PATCH notifications/{id}/read
            if (path.startsWith("/notifications/") && path.endsWith("/read")) {
                int nid = parseInt(path.substring("/notifications/".length(), path.indexOf("/read")));
                supplierService.markNotificationRead(supplierId, nid);
                write(resp, ok("Notification lue", null), 200);
                return;
            }

            // PATCH notifications/{id}/archive {archived:true|false} (défaut true : sauvegarder)
            if (path.startsWith("/notifications/") && path.endsWith("/archive")) {
                int nid = parseInt(path.substring("/notifications/".length(), path.indexOf("/archive")));
                BufferedReader areader = req.getReader();
                StringBuilder asb = new StringBuilder();
                String aline;
                while ((aline = areader.readLine()) != null) asb.append(aline);
                boolean archived = true;
                try {
                    Map<String, Object> abody = JsonUtil.fromJson(asb.toString(), Map.class);
                    if (abody.get("archived") instanceof Boolean) archived = (Boolean) abody.get("archived");
                } catch (Exception ignored) {}
                boolean done = supplierService.archiveNotification(supplierId, nid, archived);
                if (!done) { write(resp, err("Notification introuvable"), 404); return; }
                write(resp, ok(archived ? "Notification sauvegardée" : "Notification restaurée", null), 200);
                return;
            }

            write(resp, err("Route inconnue"), 404);
        } catch (Exception e) {
            write(resp, err("Impossible de traiter votre demande."), 500);
        }
    }

    // ======================= HELPERS =======================

    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", message);
        m.put("data", data);
        return m;
    }

    private Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    private Supplier safeSupplier(Supplier s) {
        if (s == null) return null;
        s.setPasswordHash(null);
        return s;
    }

    private String getUploadDir(HttpServletRequest req) {
        // Dossier persistant (survit aux redeploys) — voir UploadUtil.
        return UploadUtil.subDir(UploadUtil.SUBDIR_PRODUCTS).getAbsolutePath();
    }

    private List<String> saveParts(HttpServletRequest req) throws IOException, ServletException {
        return saveParts(req, UploadUtil.SUBDIR_PRODUCTS);
    }

    private List<String> saveParts(HttpServletRequest req, String subdir) throws IOException, ServletException {
        // Stockage persistant : les images survivent aux redeploys du WAR
        // et sont servies par UploadServlet (mappé sur /uploads/*).
        List<String> urls = new ArrayList<>();
        for (Part part : req.getParts()) {
            if ("file".equals(part.getName()) || part.getContentType() != null && part.getContentType().startsWith("image/")) {
                if (part.getSize() == 0) continue;
                urls.add(UploadUtil.saveImage(part, subdir, req));
            }
        }
        return urls;
    }

    private int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private int parseNum(String s, int def) { try { if (s == null) return def; return Integer.parseInt(s); } catch (Exception e) { return def; } }
    private Integer optInt(String s) { if (s == null || s.isEmpty()) return null; try { return Integer.parseInt(s); } catch (Exception e) { return null; } }
    private int num(Object o) { if (o == null) return 0; if (o instanceof Number) return ((Number) o).intValue(); try { return (int) Double.parseDouble(o.toString()); } catch (Exception e) { return 0; } }
    private String str(Object o) { return o == null ? null : o.toString(); }
    private List<String> stringList(Object o) {
        if (o == null) return new ArrayList<>();
        if (o instanceof List) { List<String> r = new ArrayList<>(); for (Object e : (List<?>) o) if (e != null) r.add(e.toString()); return r; }
        return new ArrayList<>();
    }
    private List<String> parseJsonList(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        try { return new ArrayList<>((List<String>) JsonUtil.fromJson(s, List.class)); } catch (Exception e) { return new ArrayList<>(); }
    }
}
