package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.service.OrderService;
import com.ecommerce.dao.DeliveryZoneDAO;
import com.ecommerce.util.JsonUtil;

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
                int id = Integer.parseInt(req.getParameter("id"));
                Order order = orderService.getOrderById(id);
                if (order != null) {
                    resp.getWriter().write(JsonUtil.toJson(order));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Commande non trouvée\"}");
                }
            } else if ("search".equals(action)) {
                String query = req.getParameter("q");
                resp.getWriter().write(JsonUtil.toJson(orderService.searchOrders(query)));
            } else if ("status".equals(action)) {
                String status = req.getParameter("status");
                resp.getWriter().write(JsonUtil.toJson(orderService.getOrdersByStatus(status)));
            } else if ("supplier".equals(action)) {
                int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                resp.getWriter().write(JsonUtil.toJson(orderService.getOrdersBySupplier(supplierId)));
            } else if ("delivery-fee".equals(action)) {
                String city = req.getParameter("city");
                int fee = deliveryDAO.getFee(city);
                resp.getWriter().write("{\"city\":\"" + city + "\",\"fee\":" + fee + "}");
            } else {
                resp.getWriter().write(JsonUtil.toJson(orderService.getAllOrders()));
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

            Order order = new Order();
            order.setFullname((String) body.get("fullname"));
            order.setPhone((String) body.get("phone"));
            order.setCity((String) body.get("city"));
            order.setDistrict((String) body.get("district"));
            order.setAddress((String) body.get("address"));
            order.setComment((String) body.get("comment"));
            order.setDeliveryMode((String) body.get("deliveryMode"));
            order.setPaymentMethod((String) body.get("paymentMethod"));

            if (body.get("supplierId") != null) {
                order.setSupplierId(((Number) body.get("supplierId")).intValue());
            }

            double total = 0;
            ArrayList<OrderItem> items = new ArrayList<>();
            ArrayList<Map<String, Object>> cartItems = (ArrayList<Map<String, Object>>) body.get("items");
            if (cartItems != null) {
                for (Map<String, Object> ci : cartItems) {
                    OrderItem item = new OrderItem();
                    item.setProductId(((Number) ci.get("productId")).intValue());
                    item.setSize((String) ci.get("size"));
                    item.setColor((String) ci.get("color"));
                    item.setQty(((Number) ci.get("qty")).intValue());
                    item.setUnitPrice(((Number) ci.get("unitPrice")).intValue());
                    total += item.getUnitPrice() * item.getQty();
                    items.add(item);
                }
            }
            // Dynamic delivery fee
            int deliveryFee = deliveryDAO.getFee(order.getCity());
            total += deliveryFee;
            order.setTotal((int) total);
            order.setItems(items);

            int orderId = orderService.createOrder(order);

            resp.getWriter().write("{\"orderId\":" + orderId + ",\"orderNumber\":\"" + order.getOrderNumber() + "\",\"status\":\"EN_ATTENTE\",\"total\":" + order.getTotal() + ",\"deliveryFee\":" + deliveryFee + "}");
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
            // Seul l'admin peut changer le statut (la création reste publique).
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = ((Number) body.get("id")).intValue();
            String status = (String) body.get("status");
            orderService.updateStatus(id, status);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            int id = Integer.parseInt(req.getParameter("id"));
            orderService.deleteOrder(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
