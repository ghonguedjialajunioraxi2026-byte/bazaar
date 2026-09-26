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
                int id = Integer.parseInt(req.getParameter("id"));
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
                // Historique invité : téléphone requis (facteur d'appartenance).
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        orderService.getOrdersByPhone(req.getParameter("phone"))));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
            } else if ("track".equals(action)) {
                // Détail invité : numéro + téléphone requis (anti-IDOR).
                try {
                    resp.getWriter().write(JsonUtil.toJson(orderService.trackOrder(
                        req.getParameter("number"), req.getParameter("phone"))));
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
                int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                resp.getWriter().write(JsonUtil.toJson(orderService.getOrdersBySupplier(supplierId)));
            } else if ("delivery-fee".equals(action)) {
                String city = req.getParameter("city");
                int fee = deliveryDAO.getFee(city);
                resp.getWriter().write("{\"city\":\"" + city + "\",\"fee\":" + fee + "}");
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

            // Annulation invitée (numéro + téléphone, EN_ATTENTE uniquement).
            if ("cancel".equals(body.get("action"))) {
                try {
                    orderService.cancelGuestOrder(
                        body.get("orderNumber") == null ? null : String.valueOf(body.get("orderNumber")),
                        body.get("phone") == null ? null : String.valueOf(body.get("phone")));
                    resp.getWriter().write("{\"success\":true,\"status\":\"ANNULEE\"}");
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }

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
            order.setCity(city);
            order.setDistrict(RequestValidator.optional(body, "district", 80));
            order.setAddress(address);
            order.setComment(RequestValidator.optional(body, "comment", 2000));
            order.setDeliveryMode(RequestValidator.optional(body, "deliveryMode", 30));
            order.setPaymentMethod(RequestValidator.optional(body, "paymentMethod", 30));

            if (body.get("supplierId") != null) {
                order.setSupplierId(((Number) body.get("supplierId")).intValue());
            }

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
            // Dynamic delivery fee
            int deliveryFee = deliveryDAO.getFee(order.getCity());
            total += deliveryFee;
            order.setTotal((int) total);
            order.setItems(items);

            int orderId = orderService.createOrder(order);

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

            int id = ((Number) body.get("id")).intValue();
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
            int id = Integer.parseInt(req.getParameter("id"));
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
}
