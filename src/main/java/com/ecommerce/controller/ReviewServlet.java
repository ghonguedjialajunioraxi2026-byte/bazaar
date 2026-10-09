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
                int productId = parseIdParam(req.getParameter("productId"), resp);
                if (productId < 0) return;
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findByProduct(productId)));
            } else if ("pending".equals(action)) {
                // File de modération : admin uniquement.
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findPending()));
            } else {
                // Historique complet : admin uniquement (modération).
                if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
                resp.getWriter().write(JsonUtil.toJson(reviewDAO.findAll()));
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

            Review r = new Review();
            int productId = parseBodyId(body, resp);
            if (productId < 0) return;
            r.setProductId(productId);
            r.setFullname(body.get("fullname") == null ? null : String.valueOf(body.get("fullname")));
            int rating = parseRating(body.get("rating"));
            if (rating < 1 || rating > 5) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Note invalide (1 à 5)\"}");
                return;
            }
            r.setRating(rating);
            r.setComment(body.get("comment") == null ? null : String.valueOf(body.get("comment")));
            // Phase 23 : produit inexistant => 404 (la FK levait un 500).
            if (new com.ecommerce.dao.ProductDAO().findById(r.getProductId()) == null) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Produit introuvable\"}");
                return;
            }
            // Anti-spam : max 5 avis/heure/produit (la modération reste le filtre).
            if (reviewDAO.countRecent(r.getProductId(), 60) >= 5) {
                resp.setStatus(429);
                resp.getWriter().write("{\"error\":\"Trop d'avis pour ce produit, réessayez plus tard\"}");
                return;
            }

            int id = reviewDAO.create(r);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true,\"message\":\"Avis en attente d'approbation\"}");
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
            // Approbation réservée à l'admin (le dépôt d'avis reste public).
            // Modération opérationnelle : tous les rôles (traçée dans l'audit).
            int modId = com.ecommerce.util.AdminAuth.requireAdmin(req, resp);
            if (modId <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            int id = parseBodyId(body, resp);
            if (id < 0) return;
            String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
            if ("approve".equals(action)) {
                reviewDAO.approve(id);
                try {
                    new com.ecommerce.dao.ActivityLogDAO().log(modId, "REVIEW_APPROVED",
                        "review", id, "Avis approuvé", req.getRemoteAddr());
                } catch (Exception ignored) {}
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(400);
                resp.getWriter().write("{\"success\":false,\"message\":\"Action inconnue\"}");
            }
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
            int delId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.DESTRUCTIVE);
            if (delId <= 0) return;
            Integer id = com.ecommerce.util.ServiceErrors.toId(req.getParameter("id"));
            if (id == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant avis requis\"}");
                return;
            }
            reviewDAO.delete(id);
            try {
                new com.ecommerce.dao.ActivityLogDAO().log(delId, "REVIEW_DELETED",
                    "review", id, "Avis supprimé", req.getRemoteAddr());
            } catch (Exception ignored) {}
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
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

    /** Note 1-5 tolérante (Number ou String), -1 si illisible. */
    private int parseRating(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o == null) return -1;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return -1; }
    }
}
