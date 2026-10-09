package com.ecommerce.controller;

import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Order;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/payments")
public class PaymentServlet extends HttpServlet {
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String pubAction = req.getParameter("action");
            // Session client éventuelle : si présente, les lectures/écritures
            // sont en plus restreintes aux commandes du compte (anti-IDOR).
            Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                com.ecommerce.util.CustomerAuth.validateCustomer(req));
            // Statut invité : numéro + téléphone (propriété vérifiée serveur).
            if ("status".equals(pubAction)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        new com.ecommerce.service.PaymentService().getStatus(
                            req.getParameter("orderNumber"), req.getParameter("phone"), session)));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Méthodes proposées (publiques, sans secret).
            if ("methods".equals(pubAction)) {
                resp.getWriter().write(JsonUtil.toJson(
                    com.ecommerce.service.PaymentProviders.offeredMethods()));
                return;
            }
            // Mode test : visible uniquement hors production (jamais de
            // secret exposé, simple interrupteur d'affichage du bouton TEST).
            if ("test-mode".equals(pubAction)) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("testMode", com.ecommerce.pawapay.PawaPayConfig.isSandbox());
                resp.getWriter().write(JsonUtil.toJson(m));
                return;
            }
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String action = req.getParameter("action");
            if ("find".equals(action)) {
                int orderId = parseIdParam(req.getParameter("orderId"), resp);
                if (orderId < 0) return;
                Payment p = paymentDAO.findByOrderId(orderId);
                if (p == null) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Paiement introuvable\"}");
                    return;
                }
                resp.getWriter().write(JsonUtil.toJson(p));
            } else if ("byId".equals(action)) {
                int id = parseIdParam(req.getParameter("id"), resp);
                if (id < 0) return;
                Payment p = paymentDAO.findById(id);
                if (p == null) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Paiement introuvable\"}");
                    return;
                }
                resp.getWriter().write(JsonUtil.toJson(p));
            } else {
                resp.getWriter().write(JsonUtil.toJson(paymentDAO.findAll()));
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
            String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
            com.ecommerce.service.PaymentService paymentService = new com.ecommerce.service.PaymentService();
            // Session client éventuelle (voir doGet) : restreint au compte si présente.
            Integer postSession = com.ecommerce.service.CustomerIsolation.sessionOf(
                com.ecommerce.util.CustomerAuth.validateCustomer(req));

            // Actions invité (propriété numéro + téléphone vérifiée serveur).
            if ("init".equals(action)) {
                try {
                    com.ecommerce.model.Payment p = paymentService.initPayment(
                        str(body.get("orderNumber")), str(body.get("phone")), str(body.get("method")), postSession);
                    resp.setStatus(201);
                    resp.getWriter().write(JsonUtil.toJson(p));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("submit-reference".equals(action)) {
                try {
                    com.ecommerce.model.Payment p = paymentService.submitReference(
                        str(body.get("orderNumber")), str(body.get("phone")), str(body.get("transactionRef")), postSession);
                    resp.getWriter().write(JsonUtil.toJson(p));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Paiement simulé MODE TEST (sandbox uniquement, vérifié serveur
            // dans PaymentService : 403 en production). Propriété numéro +
            // téléphone vérifiée comme init/submit-reference.
            if ("test-confirm".equals(action)) {
                try {
                    java.util.Map<String, Object> out = paymentService.testConfirm(
                        str(body.get("orderNumber")), str(body.get("phone")), postSession);
                    java.util.Map<String, Object> env = new java.util.LinkedHashMap<>();
                    env.put("success", true);
                    env.put("data", out);
                    resp.getWriter().write(JsonUtil.toJson(env));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }

            // Actions admin : vérification / échec (montants jamais saisis).
            // MONEY : GESTIONNAIRE exclu (lecture seule sur les paiements).
            int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                com.ecommerce.util.AdminRole.MONEY);
            if (adminId <= 0) return;
            String adminAction = action;
            try {
                if ("verify".equals(adminAction)) {
                    paymentService.verifyPayment(num(body.get("id")), adminId);
                } else if ("fail".equals(adminAction)) {
                    paymentService.failPayment(num(body.get("id")), adminId, str(body.get("reason")));
                } else {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Action requise\"}");
                    return;
                }
                resp.getWriter().write("{\"success\":true}");
            } catch (com.ecommerce.util.AppException ae) {
                com.ecommerce.util.ApiResponse.fromException(resp, ae);
            }
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int num(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    /** Parse un id de query : 400 au lieu de 500 si invalide. */
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

    /** Parse un id de corps JSON (Number ou String) : 400 si absent/invalide. */
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

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = parseBodyId(body, resp);
            if (id < 0) return;
            String status = (String) body.get("status");
            String transactionRef = (String) body.get("transactionRef");

            // Plus de statut arbitraire : verify / fail uniquement (MONEY : ADMIN+).
            int moneyAdmin = com.ecommerce.util.AdminRole.requireAction(req, resp,
                com.ecommerce.util.AdminRole.MONEY);
            if (moneyAdmin <= 0) return;
            com.ecommerce.service.PaymentService paymentService = new com.ecommerce.service.PaymentService();
            try {
                if ("REUSSIE".equals(status)) {
                    paymentService.verifyPayment(id, moneyAdmin);
                } else if ("ECHOUEE".equals(status)) {
                    paymentService.failPayment(id, moneyAdmin, transactionRef);
                } else {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Statut non supporté (REUSSIE / ECHOUEE)\"}");
                    return;
                }
            } catch (com.ecommerce.util.AppException ae) {
                com.ecommerce.util.ApiResponse.fromException(resp, ae);
                return;
            }

            Payment p = paymentDAO.findById(id);
            if (p != null) {
                Order order = orderDAO.findById(p.getOrderId());
                if (order != null) {
                    notificationDAO.notifyPayment(order.getOrderNumber(), p.getAmount(), status);
                }
            }

            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
