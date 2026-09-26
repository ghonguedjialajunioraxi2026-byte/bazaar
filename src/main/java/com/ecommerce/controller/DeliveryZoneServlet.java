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
                resp.getWriter().write("{\"city\":\"" + city + "\",\"fee\":" + fee + "}");
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

            DeliveryZone dz = new DeliveryZone();
            dz.setCity((String) body.get("city"));
            dz.setZone((String) body.get("zone"));
            dz.setFee(((Number) body.get("fee")).intValue());
            dz.setActive(body.get("active") == null || (Boolean) body.get("active"));

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

            DeliveryZone dz = new DeliveryZone();
            dz.setId(((Number) body.get("id")).intValue());
            dz.setCity((String) body.get("city"));
            dz.setZone((String) body.get("zone"));
            dz.setFee(((Number) body.get("fee")).intValue());
            dz.setActive(body.get("active") == null || (Boolean) body.get("active"));

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
            int id = Integer.parseInt(req.getParameter("id"));
            deliveryDAO.delete(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
