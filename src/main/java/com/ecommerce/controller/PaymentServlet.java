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
            // Statut invité : numéro + téléphone (propriété vérifiée serveur).
            if ("status".equals(pubAction)) {
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        new com.ecommerce.service.PaymentService().getStatus(
                            req.getParameter("orderNumber"), req.getParameter("phone"))));
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
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String action = req.getParameter("action");
            if ("find".equals(action)) {
                int orderId = Integer.parseInt(req.getParameter("orderId"));
                Payment p = paymentDAO.findByOrderId(orderId);
                resp.getWriter().write(JsonUtil.toJson(p));
            } else if ("byId".equals(action)) {
                int id = Integer.parseInt(req.getParameter("id"));
                Payment p = paymentDAO.findById(id);
                resp.getWriter().write(JsonUtil.toJson(p));
            } else {
                resp.getWriter().write(JsonUtil.toJson(paymentDAO.findAll()));
            }
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
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);
            String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
            com.ecommerce.service.PaymentService paymentService = new com.ecommerce.service.PaymentService();

            // Actions invité (propriété numéro + téléphone vérifiée serveur).
            if ("init".equals(action)) {
                try {
                    com.ecommerce.model.Payment p = paymentService.initPayment(
                        str(body.get("orderNumber")), str(body.get("phone")), str(body.get("method")));
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
                        str(body.get("orderNumber")), str(body.get("phone")), str(body.get("transactionRef")));
                    resp.getWriter().write(JsonUtil.toJson(p));
                } catch (com.ecommerce.util.AppException ae) {
                    com.ecommerce.util.ApiResponse.fromException(resp, ae);
                }
                return;
            }

            // Actions admin : vérification / échec (montants jamais saisis).
            int adminId = com.ecommerce.util.AdminAuth.requireAdmin(req, resp);
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
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int num(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
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

            int id = ((Number) body.get("id")).intValue();
            String status = (String) body.get("status");
            String transactionRef = (String) body.get("transactionRef");

            // Plus de statut arbitraire : verify / fail uniquement.
            com.ecommerce.service.PaymentService paymentService = new com.ecommerce.service.PaymentService();
            try {
                if ("REUSSIE".equals(status)) {
                    paymentService.verifyPayment(id, com.ecommerce.util.AdminAuth.requireAdmin(req, resp));
                } else if ("ECHOUEE".equals(status)) {
                    paymentService.failPayment(id, com.ecommerce.util.AdminAuth.requireAdmin(req, resp), transactionRef);
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
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
