package com.ecommerce.controller;

import com.ecommerce.model.Supplier;
import com.ecommerce.service.SupplierService;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/suppliers")
public class SupplierServlet extends HttpServlet {
    private final SupplierService supplierService = new SupplierService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String action = req.getParameter("action");
            if ("active".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(supplierService.getActive()));
            } else if ("find".equals(action)) {
                int id = Integer.parseInt(req.getParameter("id"));
                Supplier s = supplierService.getById(id);
                if (s != null) {
                    resp.getWriter().write(JsonUtil.toJson(s));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Fournisseur non trouvé\"}");
                }
            } else {
                resp.getWriter().write(JsonUtil.toJson(supplierService.getAll()));
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

            Supplier s = new Supplier();
            s.setName((String) body.get("name"));
            s.setPhone((String) body.get("phone"));
            s.setWhatsapp((String) body.get("whatsapp"));
            s.setEmail((String) body.get("email"));
            s.setContactName((String) body.get("contactName"));
            s.setCity((String) body.get("city"));
            s.setAddress((String) body.get("address"));
            s.setDescription((String) body.get("description"));
            s.setActive(body.get("active") == null || (Boolean) body.get("active"));
            s.setCommissionRate(10.0);

            String password = (String) body.get("password");
            if (password != null && !password.isEmpty()) {
                s.setPasswordHash(com.ecommerce.util.PasswordUtil.hash(password));
            }

            int id = supplierService.createSupplier(s);
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
            int adminId = com.ecommerce.util.AdminAuth.requireAdmin(req, resp);
            if (adminId <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            Supplier s = new Supplier();
            s.setId(((Number) body.get("id")).intValue());
            s.setName((String) body.get("name"));
            s.setPhone((String) body.get("phone"));
            s.setWhatsapp((String) body.get("whatsapp"));
            s.setEmail((String) body.get("email"));
            s.setCity((String) body.get("city"));
            s.setAddress((String) body.get("address"));
            s.setActive(body.get("active") == null || (Boolean) body.get("active"));

            supplierService.updateSupplier(s);
            try {
                new com.ecommerce.dao.ActivityLogDAO().log(adminId,
                    s.isActive() ? "SUPPLIER_ACTIVATED" : "SUPPLIER_SUSPENDED",
                    "supplier", s.getId(),
                    "Fournisseur " + (s.isActive() ? "activé" : "suspendu"), req.getRemoteAddr());
            } catch (Exception ignored) {}
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
            supplierService.deleteSupplier(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
