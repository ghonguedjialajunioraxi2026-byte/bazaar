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
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            Payment p = new Payment();
            p.setOrderId(((Number) body.get("orderId")).intValue());
            p.setMethod((String) body.get("method"));
            p.setAmount(((Number) body.get("amount")).intValue());
            p.setTransactionRef((String) body.get("transactionRef"));

            // For WhatsApp / Cash on delivery, auto-mark as EN_ATTENTE
            if ("WHATSAPP".equals(p.getMethod()) || "PAIEMENT_LIVRAISON".equals(p.getMethod())) {
                p.setStatus("EN_ATTENTE");
            } else {
                // Mobile Money / Card: mark as pending, would need real integration later
                p.setStatus("EN_ATTENTE");
            }

            int id = paymentDAO.create(p);

            // Notification
            Order order = orderDAO.findById(p.getOrderId());
            if (order != null) {
                notificationDAO.notifyPayment(order.getOrderNumber(), p.getAmount(), p.getStatus());
            }

            resp.getWriter().write("{\"id\":" + id + ",\"status\":\"" + p.getStatus() + "\",\"success\":true}");
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
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = ((Number) body.get("id")).intValue();
            String status = (String) body.get("status");
            String transactionRef = (String) body.get("transactionRef");

            paymentDAO.updateStatus(id, status, transactionRef);

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
