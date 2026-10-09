package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.service.OrderService;
import com.ecommerce.dao.DeliveryZoneDAO;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;
import com.ecommerce.util.RequestValidator;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

@WebServlet("/api/orders")
public class OrderServlet extends HttpServlet {
    private final OrderService orderService = new OrderService();
    private final DeliveryZoneDAO deliveryDAO = new DeliveryZoneDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String action = req.getParameter("action");
            if ("find".equals(action)) {
                // Lecture admin uniquement (PII : nom/téléphone/adresse).
                // Les invités utilisent track (numéro + téléphone).
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                int id = parseIdParam(req.getParameter("id"), resp);
                if (id < 0) return;
                Order order = orderService.getOrderById(id);
                if (order != null) {
                    resp.getWriter().write(JsonUtil.toJson(order));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Commande non trouvée\"}");
                }
            } else if ("search".equals(action)) {
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                String query = req.getParameter("q");
                resp.getWriter().write(JsonUtil.toJson(orderService.searchOrders(query)));
            } else if ("myorders".equals(action)) {
                // MES COMMANDES (isolation de compte) : client connecté ->
                // UNIQUEMENT ses commandes (le téléphone éventuel est ignoré,
                // jamais une source d'identité). Invité sans session ->
                // téléphone requis comme preuve d'appartenance.
                // Sans les deux : 401 (on ne liste jamais tout le monde).
                try {
                    int sessionId = com.ecommerce.util.CustomerAuth.validateCustomer(req);
                    if (sessionId > 0) {
                        resp.getWriter().write(JsonUtil.toJson(
                            orderService.getOrdersByCustomer(sessionId)));
                    } else {
                        String phoneParam = req.getParameter("phone");
                        if (phoneParam != null && !phoneParam.trim().isEmpty()) {
                            resp.getWriter().write(JsonUtil.toJson(
                                orderService.getOrdersByPhone(phoneParam)));
                        } else {
                            resp.getWriter().write(JsonUtil.toJson(
                                orderService.getOrdersByCustomer(sessionId)));
                        }
                    }
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
            } else if ("track".equals(action)) {
                // Détail : numéro + téléphone requis, propriété du compte
                // vérifiée si une session est présente (anti-IDOR).
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    resp.getWriter().write(JsonUtil.toJson(orderService.trackOrder(
                        req.getParameter("number"), req.getParameter("phone"), session)));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
            } else if ("full".equals(action)) {
                // Vue complète suivi client (1 appel) : commande + paiement +
                // livraison + litige + refund + journal. Même preuve que track.
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    resp.getWriter().write(JsonUtil.toJson(orderService.getOrderFullView(
                        req.getParameter("number"), req.getParameter("phone"), session)));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
            } else if ("status".equals(action)) {
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                String status = req.getParameter("status");
                resp.getWriter().write(JsonUtil.toJson(orderService.getOrdersByStatus(status)));
            } else if ("supplier".equals(action)) {
                // IDOR corrigé : seul le périmètre admin (ou l'API fournisseur
                // scopée au token) expose les commandes d'un vendeur.
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                int supplierId = parseIdParam(req.getParameter("supplierId"), resp);
                if (supplierId < 0) return;
                resp.getWriter().write(JsonUtil.toJson(orderService.getOrdersBySupplier(supplierId)));
            } else if ("delivery-fee".equals(action)) {
                String city = req.getParameter("city");
                if (city == null || city.trim().isEmpty()) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Ville requise\"}");
                    return;
                }
                int fee = deliveryDAO.getFee(city);
                java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
                out.put("city", city);
                out.put("fee", fee);
                resp.getWriter().write(JsonUtil.toJson(out));
            } else {
                // Liste complète : admin uniquement (dashboard avec token).
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                resp.getWriter().write(JsonUtil.toJson(orderService.getAllOrders()));
            }
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
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
            if (body == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Corps JSON requis\"}");
                return;
            }

            // Annulation invitée (numéro + téléphone, EN_ATTENTE uniquement,
            // propriété du compte vérifiée si une session est présente).
            if ("cancel".equals(body.get("action"))) {
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    orderService.cancelGuestOrder(
                        body.get("orderNumber") == null ? null : String.valueOf(body.get("orderNumber")),
                        body.get("phone") == null ? null : String.valueOf(body.get("phone")),
                        session);
                    resp.getWriter().write("{\"success\":true,\"status\":\"ANNULEE\"}");
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }

            // Création : session client OBLIGATOIRE (l'API seule ne suffit pas ;
            // le téléphone reste la preuve d'appartenance pour PawaPay/suivi).
            int customerId = com.ecommerce.util.CustomerAuth.requireCustomer(req, resp);
            if (customerId <= 0) return;

            // Validation serveur (le frontend valide aussi, sans être une frontière).
            String fullname = RequestValidator.required(body, "fullname", "Nom complet");
            RequestValidator.maxLen(fullname, 120, "Nom complet");
            String phone = RequestValidator.required(body, "phone", "Téléphone");
            RequestValidator.phone(phone, "Téléphone");
            String city = RequestValidator.required(body, "city", "Ville");
            RequestValidator.maxLen(city, 60, "Ville");
            String address = RequestValidator.required(body, "address", "Adresse");
            RequestValidator.maxLen(address, 255, "Adresse");

            Order order = new Order();
            order.setFullname(fullname);
            order.setPhone(phone);
            // Email optionnel (notifications email ; vide = pas d'email).
            String email = RequestValidator.optional(body, "email", 160);
            if (email != null && !email.trim().isEmpty()) {
                RequestValidator.email(email.trim(), "Email");
                order.setEmail(email.trim().toLowerCase());
            }
            order.setCity(city);
            order.setDistrict(RequestValidator.optional(body, "district", 80));
            order.setAddress(address);
            order.setComment(RequestValidator.optional(body, "comment", 2000));
            order.setDeliveryMode(RequestValidator.optional(body, "deliveryMode", 30));
            order.setPaymentMethod(RequestValidator.optional(body, "paymentMethod", 30));

            if (body.get("supplierId") != null) {
                order.setSupplierId(((Number) body.get("supplierId")).intValue());
            }
            order.setCustomerId(customerId);

            double total = 0;
            ArrayList<OrderItem> items = new ArrayList<>();
            ArrayList<Map<String, Object>> cartItems = (ArrayList<Map<String, Object>>) body.get("items");
            if (cartItems == null || cartItems.isEmpty()) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Panier vide");
            }
            if (cartItems.size() > 100) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Trop d'articles (max 100)");
            }
            for (Map<String, Object> ci : cartItems) {
                OrderItem item = new OrderItem();
                item.setProductId(RequestValidator.requiredPositiveInt(ci, "productId", "Produit"));
                item.setSize(RequestValidator.optional(ci, "size", 20));
                item.setColor(RequestValidator.optional(ci, "color", 40));
                item.setQty(RequestValidator.requiredPositiveInt(ci, "qty", "Quantité"));
                if (item.getQty() > 99) {
                    throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Quantité invalide (max 99)");
                }
                Object oid = ci.get("offerId");
                if (oid instanceof Number && ((Number) oid).intValue() > 0) {
                    item.setOfferId(((Number) oid).intValue());
                }
                Object up = ci.get("unitPrice");
                int unitPrice = (up instanceof Number) ? ((Number) up).intValue() : -1;
                if (unitPrice < 0) {
                    throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Prix invalide");
                }
                item.setUnitPrice(unitPrice);
                total += item.getUnitPrice() * item.getQty();
                items.add(item);
            }
            // Dynamic delivery fee (estimation affichée ; le total effectif est
            // recalculé côté OrderService : prix fournisseur prioritaire,
            // sinon tarif zone).
            int deliveryFee = deliveryDAO.getFee(order.getCity());
            int itemsSum = (int) total;
            total += deliveryFee;
            order.setTotal((int) total);
            order.setItems(items);

            int orderId = orderService.createOrder(order);
            // Sous-total recalculé depuis les lignes SERVEUR (le service impose
            // les prix catalogue : la somme client est ignorée et fausserait
            // l'affichage des frais si les prix envoyés étaient manipulés).
            int serverItemsSum = 0;
            if (order.getItems() != null) {
                for (OrderItem it : order.getItems()) {
                    serverItemsSum += it.getUnitPrice() * it.getQty();
                }
            }
            deliveryFee = Math.max(0, order.getTotal() - serverItemsSum);

            // Format historique conservé + champs standardisés (additif).
            resp.getWriter().write("{\"success\":true,\"orderId\":" + orderId
                + ",\"orderNumber\":\"" + order.getOrderNumber() + "\",\"status\":\"EN_ATTENTE\",\"total\":"
                + order.getTotal() + ",\"deliveryFee\":" + deliveryFee + ",\"message\":null,\"errorCode\":null}");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            // Seul l'admin peut changer le statut (la création reste publique).
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = parseBodyId(body, resp);
            if (id < 0) return;
            String status = (String) body.get("status");
            try {
                orderService.updateStatus(id, status);
            } catch (AppException ae) {
                ApiResponse.fromException(resp, ae);
                return;
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

        @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.DESTRUCTIVE) <= 0) return;
            int id = parseIdParam(req.getParameter("id"), resp);
            if (id < 0) return;
            try {
                orderService.deleteOrder(id);
            } catch (AppException ae) {
                ApiResponse.fromException(resp, ae);
                return;
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    /** Parse un id de query (?id=, ?supplierId=) : 400 au lieu de 500 si invalide. */
    private int parseIdParam(String raw, HttpServletResponse resp) throws IOException {
        try {
            int id = Integer.parseInt(raw);
            if (id <= 0) throw new NumberFormatException("id");
            return id;
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Identifiant invalide\"}");
            return -1;
        }
    }

    /** Parse un id de corps JSON (Gson -> Number ou String) : 400 si absent/invalide. */
    private int parseBodyId(Map<String, Object> body, HttpServletResponse resp) throws IOException {
        Object o = body == null ? null : body.get("id");
        int id = -1;
        if (o instanceof Number) id = ((Number) o).intValue();
        else if (o != null) {
            try { id = Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException ignored) {}
        }
        if (id <= 0) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Identifiant invalide\"}");
            return -1;
        }
        return id;
    }
}
