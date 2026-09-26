package com.ecommerce.controller;

import com.ecommerce.model.Review;
import com.ecommerce.dao.ReviewDAO;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/reviews")
public class ReviewServlet extends HttpServlet {
    private final ReviewDAO reviewDAO = new ReviewDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String action = req.getParameter("action");
            if ("product".equals(action)) {
                int productId = Integer.parseInt(req.getParameter("productId"));
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findByProduct(productId)));
            } else if ("pending".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findPending()));
            } else {
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findAll()));
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

            Review r = new Review();
            r.setProductId(((Number) body.get("productId")).intValue());
            r.setFullname((String) body.get("fullname"));
            r.setRating(((Number) body.get("rating")).intValue());
            r.setComment((String) body.get("comment"));

            int id = reviewDAO.create(r);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true,\"message\":\"Avis en attente d'approbation\"}");
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
            // Approbation réservée à l'admin (le dépôt d'avis reste public).
            // Modération opérationnelle : tous les rôles (traçée dans l'audit).
            int modId = com.ecommerce.util.AdminAuth.requireAdmin(req, resp);
            if (modId <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = ((Number) body.get("id")).intValue();
            String action = (String) body.get("action");
            if ("approve".equals(action)) {
                reviewDAO.approve(id);
                try {
                    new com.ecommerce.dao.ActivityLogDAO().log(modId, "REVIEW_APPROVED",
                        "review", id, "Avis approuvé", req.getRemoteAddr());
                } catch (Exception ignored) {}
            }
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
            int delId = com.ecommerce.util.AdminAuth.requireAdmin(req, resp);
            if (delId <= 0) return;
            int id = Integer.parseInt(req.getParameter("id"));
            reviewDAO.delete(id);
            try {
                new com.ecommerce.dao.ActivityLogDAO().log(delId, "REVIEW_DELETED",
                    "review", id, "Avis supprimé", req.getRemoteAddr());
            } catch (Exception ignored) {}
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
