package com.ecommerce.controller;

import com.ecommerce.service.DeliveryFlowService;
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
 * Livraison BAZAAR v2 (2 actions explicites).
 *
 * <pre>
 *   POST /api/delivery-flow/start-delivery     {orderId}                 -> FOURNISSEUR propriétaire
 *   POST /api/delivery-flow/confirm-receipt    {orderNumber, phone}      -> CLIENT propriétaire (preuve téléphone)
 *   POST /api/delivery-flow/validate-delivery  {orderId}                 -> FOURNISSEUR propriétaire
 *   GET  /api/delivery-flow/state?orderId=     (fournisseur)             -> état du flux
 *   GET  /api/delivery-flow/state?orderNumber=&amp;phone= (client/admin)  -> état du flux
 * </pre>
 *
 * Auth : Bearer header seul (jamais ?token=). Le client invité prouve la
 * propriété par numéro + téléphone (comme le suivi existant) ; un éventuel
 * token client incohérent avec la commande est rejeté.
 * Le payout ne peut partir que via validate-delivery (jamais d'appel direct
 * fournisseur vers /api/payouts : cette route n'existe tout simplement pas).
 */
@WebServlet("/api/delivery-flow/*")
public class DeliveryFlowServlet extends HttpServlet {

    private final DeliveryFlowService flow = new DeliveryFlowService();
    private final SupplierAuthService supplierAuthService = new SupplierAuthService();

    private Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        String raw = sb.toString().trim();
        if (raw.isEmpty()) return new java.util.LinkedHashMap<>();
        return JsonUtil.fromJson(raw, Map.class);
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
            else token = null;
            if (token == null || token.isBlank()) return null;
            int id = supplierAuthService.validateToken(token);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] pathParts(HttpServletRequest req) {
        String pi = req.getPathInfo();
        if (pi == null || pi.equals("/")) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : pi.split("/")) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            if (parts.length != 1 || !"state".equals(parts[0])) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            int orderId = num(req.getParameter("orderId"), 0);
            String orderNumber = req.getParameter("orderNumber");
            String phone = req.getParameter("phone");
            Integer supplierId = supplierIdOf(req);
            if (orderId > 0 && supplierId != null) {
                // Le service répond 404 si la commande n'appartient pas au fournisseur.
                ApiResponse.ok(resp, flow.flowStateVisibleToSupplier(orderId, supplierId));
                return;
            }
            if (orderNumber != null && phone != null) {
                // Preuve téléphone (client) ou supervision admin.
                boolean admin = AdminAuth.validateAdmin(req) > 0;
                Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                    com.ecommerce.util.CustomerAuth.validateCustomer(req));
                ApiResponse.ok(resp, flow.flowStateVisibleToClient(orderNumber, phone, admin, session));
                return;
            }
            ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            Map<String, Object> body = readBody(req);
            if (parts.length == 1 && "start-delivery".equals(parts[0])) {
                Integer supplierId = supplierIdOf(req);
                if (supplierId == null) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                int orderId = num(body.get("orderId"), 0);
                if (orderId <= 0) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "orderId requis");
                    return;
                }
                Map<String, Object> out = flow.startDelivery(supplierId, orderId);
                boolean dup = Boolean.TRUE.equals(out.get("duplicate"));
                if (dup) ApiResponse.ok(resp, out);
                else ApiResponse.created(resp, out);
                return;
            }
            if (parts.length == 1 && "confirm-receipt".equals(parts[0])) {
                String orderNumber = str(body.get("orderNumber"));
                String phone = str(body.get("phone"));
                if (orderNumber == null || orderNumber.isBlank()) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                        "orderNumber requis");
                    return;
                }
                // Session client prioritaire (identité serveur, jamais le clientId du navigateur).
                int customerId = 0;
                try {
                    customerId = com.ecommerce.util.CustomerAuth.validateCustomer(req);
                } catch (Exception ignored) {
                    customerId = 0;
                }
                if (customerId > 0) {
                    // Token incohérent avec une commande d'autrui = 403 (pas de repli téléphone).
                    Map<String, Object> out = flow.confirmReceiptByCustomer(customerId, orderNumber.trim());
                    ApiResponse.ok(resp, out);
                    return;
                }
                if (phone == null || phone.isBlank()) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                        "orderNumber et phone requis");
                    return;
                }
                // Invité sans session : preuve téléphone (+ propriété vérifiée
                // si une session existe par ailleurs — ici customerId == 0).
                Map<String, Object> out = flow.confirmReceiptByNumber(orderNumber.trim(), phone.trim(), null);
                ApiResponse.ok(resp, out);
                return;
            }
            if (parts.length == 1 && "validate-delivery".equals(parts[0])) {
                Integer supplierId = supplierIdOf(req);
                if (supplierId == null) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                int orderId = num(body.get("orderId"), 0);
                if (orderId <= 0) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "orderId requis");
                    return;
                }
                Map<String, Object> out = flow.validateDelivery(supplierId, orderId);
                boolean vdup = Boolean.TRUE.equals(out.get("duplicate"));
                if (vdup) ApiResponse.ok(resp, out);
                else ApiResponse.created(resp, out);
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }
}
