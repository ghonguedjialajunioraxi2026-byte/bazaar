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
            } else if ("all".equals(action)) {
                // Gestion admin : toutes les promos (actives + inactives).
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                resp.getWriter().write(JsonUtil.toJson(promotionDAO.findAll()));
            } else if ("byProduct".equals(action)) {
                int productId;
                try {
                    productId = Integer.parseInt(req.getParameter("productId"));
                    if (productId <= 0) throw new NumberFormatException("productId");
                } catch (Exception e) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Identifiant produit invalide\"}");
                    return;
                }
                Promotion p = promotionDAO.findByProductId(productId);
                resp.getWriter().write(JsonUtil.toJson(p));
            } else {
                // Vitrine publique : seules les promotions actives.
                // (findAll exposerait les promos inactives/futures.)
                resp.getWriter().write(JsonUtil.toJson(promotionDAO.findActive()));
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
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
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

            Promotion p = new Promotion();
            if (!(body.get("productId") instanceof Number) || ((Number) body.get("productId")).intValue() <= 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant produit invalide\"}");
                return;
            }
            if (!(body.get("promoPrice") instanceof Number) || ((Number) body.get("promoPrice")).intValue() < 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Prix promotionnel invalide\"}");
                return;
            }
            p.setProductId(((Number) body.get("productId")).intValue());
            p.setPromoPrice(((Number) body.get("promoPrice")).intValue());
            p.setPromoPercent(body.get("promoPercent") != null ? ((Number) body.get("promoPercent")).intValue() : 0);
            p.setStartDate((String) body.get("startDate"));
            p.setEndDate((String) body.get("endDate"));
            Boolean createdActive = com.ecommerce.util.ServiceErrors.toBoolean(body.get("active"));
            p.setActive(createdActive == null || createdActive);

            int id = promotionDAO.create(p);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
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
            if (body == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Corps JSON requis\"}");
                return;
            }
            if (!(body.get("id") instanceof Number) || ((Number) body.get("id")).intValue() <= 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant invalide\"}");
                return;
            }
            if (!(body.get("promoPrice") instanceof Number) || ((Number) body.get("promoPrice")).intValue() < 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Prix promotionnel invalide\"}");
                return;
            }

            Promotion p = new Promotion();
            p.setId(((Number) body.get("id")).intValue());
            p.setPromoPrice(((Number) body.get("promoPrice")).intValue());
            p.setPromoPercent(body.get("promoPercent") != null ? ((Number) body.get("promoPercent")).intValue() : 0);
            p.setStartDate((String) body.get("startDate"));
            p.setEndDate((String) body.get("endDate"));
            Boolean updatedActive = com.ecommerce.util.ServiceErrors.toBoolean(body.get("active"));
            p.setActive(updatedActive == null || updatedActive);

            promotionDAO.update(p);
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
            Integer id = com.ecommerce.util.ServiceErrors.toId(req.getParameter("id"));
            if (id == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant promotion requis\"}");
                return;
            }
            promotionDAO.delete(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
