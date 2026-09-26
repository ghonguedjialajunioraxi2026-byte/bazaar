package com.ecommerce.controller;

import com.ecommerce.model.Shipment;
import com.ecommerce.service.ConversationService;
import com.ecommerce.service.ShipmentService;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.util.AdminAuth;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * Expéditions Phase 12. Trois rôles :
 * - Invité : suivi par numéro + téléphone, confirmation de réception.
 * - Fournisseur : Bearer, uniquement ses commandes.
 * - Admin : token admin, supervision.
 */
@WebServlet("/api/shipments/*")
public class ShipmentServlet extends HttpServlet {

    private final ShipmentService shipmentService = new ShipmentService();
    private final SupplierAuthService supplierAuthService = new SupplierAuthService();

    private Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return JsonUtil.fromJson(sb.toString(), Map.class);
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int num(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private Integer supplierIdOf(HttpServletRequest req) {
        try {
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else token = req.getParameter("token");
            if (token == null || token.isBlank()) return null;
            int id = supplierAuthService.validateToken(token);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int adminIdOf(HttpServletRequest req) {
        return AdminAuth.validateAdmin(req);
    }

    private String[] pathParts(HttpServletRequest req) {
        String pi = req.getPathInfo();
        if (pi == null || pi.equals("/")) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : pi.split("/")) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            // Suivi invité : numéro + téléphone.
            if (parts.length == 1 && "track".equals(parts[0])) {
                ApiResponse.ok(resp, shipmentService.detailForGuest(
                    req.getParameter("number"), req.getParameter("phone")));
                return;
            }
            // Expédition d'une commande (invité propriétaire : numéro + téléphone).
            if (parts.length == 2 && "by-order".equals(parts[0])) {
                String orderNumber = parts[1];
                String phone = req.getParameter("phone");
                com.ecommerce.model.Order o;
                try {
                    o = new com.ecommerce.service.OrderService().trackOrder(orderNumber, phone);
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                    return;
                }
                com.ecommerce.model.Shipment s;
                try {
                    s = new com.ecommerce.dao.ShipmentDAO().findByOrder(o.getId());
                } catch (java.sql.SQLException e) {
                    ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
                    return;
                }
                if (s == null) {
                    ApiResponse.ok(resp, null);
                    return;
                }
                ApiResponse.ok(resp, shipmentService.detailForGuest(s.getTrackingNumber(), phone));
                return;
            }
            // Détail par commande (fournisseur propriétaire ou admin).
            if (parts.length == 2 && "order".equals(parts[0])) {
                int orderId = Integer.parseInt(parts[1]);
                Integer supplierId = supplierIdOf(req);
                if (supplierId == null && adminIdOf(req) <= 0) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                ApiResponse.ok(resp, shipmentService.detailForOrder(orderId, supplierId));
                return;
            }
            // Supervision admin : expéditions actives.
            if (parts.length == 1 && "active".equals(parts[0])) {
                if (adminIdOf(req) <= 0) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                ApiResponse.ok(resp, new com.ecommerce.dao.ShipmentDAO().findActive(100));
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            Map<String, Object> body = readBody(req);
            // Confirmation de réception client : numéro + téléphone (pas de token).
            if (parts.length == 1 && "confirm-received".equals(parts[0])) {
                Shipment s = shipmentService.confirmReceipt(str(body.get("trackingNumber")), str(body.get("phone")));
                ApiResponse.ok(resp, s);
                return;
            }
            Integer supplierId = supplierIdOf(req);
            boolean admin = supplierId == null && adminIdOf(req) > 0;
            if (supplierId == null && !admin) {
                ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                return;
            }
            // Création : POST /api/shipments {orderId}
            if (parts.length == 0) {
                Shipment s = shipmentService.createShipment(num(body.get("orderId"), 0),
                    supplierId, supplierId != null ? "SUPPLIER" : "ADMIN");
                resp.setStatus(201);
                resp.getWriter().write(JsonUtil.toJson(s));
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
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
        try {
            String[] parts = pathParts(req);
            if (parts.length != 2 || !"status".equals(parts[1])) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            int shipmentId = Integer.parseInt(parts[0]);
            Map<String, Object> body = readBody(req);
            Integer supplierId = supplierIdOf(req);
            boolean admin = supplierId == null && adminIdOf(req) > 0;
            if (supplierId == null && !admin) {
                ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                return;
            }
            Shipment s = shipmentService.advance(shipmentId, str(body.get("status")),
                str(body.get("reason")), supplierId != null ? "SUPPLIER" : "ADMIN", supplierId);
            ApiResponse.ok(resp, s);
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }
}
