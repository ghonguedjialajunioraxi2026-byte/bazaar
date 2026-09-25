package com.ecommerce.controller;

import com.ecommerce.dao.PromotionDAO;
import com.ecommerce.model.Promotion;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/promotions")
public class PromotionServlet extends HttpServlet {
    private final PromotionDAO promotionDAO = new PromotionDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String action = req.getParameter("action");
            if ("active".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(promotionDAO.findActive()));
            } else if ("byProduct".equals(action)) {
                int productId = Integer.parseInt(req.getParameter("productId"));
                Promotion p = promotionDAO.findByProductId(productId);
                resp.getWriter().write(JsonUtil.toJson(p));
            } else {
                resp.getWriter().write(JsonUtil.toJson(promotionDAO.findAll()));
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

            Promotion p = new Promotion();
            p.setProductId(((Number) body.get("productId")).intValue());
            p.setPromoPrice(((Number) body.get("promoPrice")).intValue());
            p.setPromoPercent(body.get("promoPercent") != null ? ((Number) body.get("promoPercent")).intValue() : 0);
            p.setStartDate((String) body.get("startDate"));
            p.setEndDate((String) body.get("endDate"));
            p.setActive(body.get("active") == null || (Boolean) body.get("active"));

            int id = promotionDAO.create(p);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true}");
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

            Promotion p = new Promotion();
            p.setId(((Number) body.get("id")).intValue());
            p.setPromoPrice(((Number) body.get("promoPrice")).intValue());
            p.setPromoPercent(body.get("promoPercent") != null ? ((Number) body.get("promoPercent")).intValue() : 0);
            p.setStartDate((String) body.get("startDate"));
            p.setEndDate((String) body.get("endDate"));
            p.setActive(body.get("active") == null || (Boolean) body.get("active"));

            promotionDAO.update(p);
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
            promotionDAO.delete(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
