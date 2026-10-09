package com.ecommerce.controller;

import com.ecommerce.model.DeliveryZone;
import com.ecommerce.dao.DeliveryZoneDAO;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/delivery")
public class DeliveryZoneServlet extends HttpServlet {
    private final DeliveryZoneDAO deliveryDAO = new DeliveryZoneDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String city = req.getParameter("city");
            if (city != null) {
                int fee = deliveryDAO.getFee(city);
                java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
                out.put("city", city);
                out.put("fee", fee);
                resp.getWriter().write(JsonUtil.toJson(out));
            } else {
                resp.getWriter().write(JsonUtil.toJson(deliveryDAO.findAll()));
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

            DeliveryZone dz = new DeliveryZone();
            String city = body.get("city") == null ? null : String.valueOf(body.get("city")).trim();
            String zone = body.get("zone") == null ? null : String.valueOf(body.get("zone")).trim();
            if (city == null || city.isEmpty() || city.length() > 60
                    || zone == null || zone.isEmpty() || zone.length() > 80) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Ville et zone requises (max 60/80 caractères)\"}");
                return;
            }
            if (!(body.get("fee") instanceof Number) || ((Number) body.get("fee")).intValue() < 0
                    || ((Number) body.get("fee")).intValue() > 100000) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Frais de livraison invalides (0 à 100000)\"}");
                return;
            }
            dz.setCity(city);
            dz.setZone(zone);
            dz.setFee(((Number) body.get("fee")).intValue());
            Object active = body.get("active");
            dz.setActive(active == null || Boolean.TRUE.equals(active)
                || "true".equalsIgnoreCase(String.valueOf(active)));

            int id = deliveryDAO.create(dz);
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
            Object rawId = body.get("id");
            int dzId = -1;
            if (rawId instanceof Number) dzId = ((Number) rawId).intValue();
            else if (rawId != null) {
                try { dzId = Integer.parseInt(String.valueOf(rawId).trim()); } catch (NumberFormatException ignored) {}
            }
            if (dzId <= 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant invalide\"}");
                return;
            }

            DeliveryZone dz = new DeliveryZone();
            dz.setId(dzId);
            String city = body.get("city") == null ? null : String.valueOf(body.get("city")).trim();
            String zone = body.get("zone") == null ? null : String.valueOf(body.get("zone")).trim();
            if (city == null || city.isEmpty() || city.length() > 60
                    || zone == null || zone.isEmpty() || zone.length() > 80) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Ville et zone requises (max 60/80 caractères)\"}");
                return;
            }
            if (!(body.get("fee") instanceof Number) || ((Number) body.get("fee")).intValue() < 0
                    || ((Number) body.get("fee")).intValue() > 100000) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Frais de livraison invalides (0 à 100000)\"}");
                return;
            }
            dz.setCity(city);
            dz.setZone(zone);
            dz.setFee(((Number) body.get("fee")).intValue());
            Object active = body.get("active");
            dz.setActive(active == null || Boolean.TRUE.equals(active)
                || "true".equalsIgnoreCase(String.valueOf(active)));

            deliveryDAO.update(dz);
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
            int id;
            try {
                id = Integer.parseInt(req.getParameter("id"));
                if (id <= 0) throw new NumberFormatException("id");
            } catch (Exception e) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant invalide\"}");
                return;
            }
            deliveryDAO.delete(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
