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
            // Vitrine publique : jamais de hash, email, téléphones, soldes.
            // Le dashboard admin joint son Bearer et reçoit les fiches complètes
            // (hash mot de passe exclu dans tous les cas, jamais exposé).
            boolean admin = com.ecommerce.util.AdminAuth.validateAdmin(req) > 0;
            String action = req.getParameter("action");
            if ("active".equals(action)) {
                resp.getWriter().write(JsonUtil.toJson(admin
                    ? safeShops(supplierService.getActive()) : publicShops(supplierService.getActive())));
            } else if ("find".equals(action)) {
                int id = parseIdParam(req.getParameter("id"), resp);
                if (id < 0) return;
                Supplier s = supplierService.getById(id);
                if (s == null || (!admin && !s.isActive())) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Fournisseur non trouvé\"}");
                } else {
                    resp.getWriter().write(JsonUtil.toJson(admin ? safeShop(s) : publicShop(s)));
                }
            } else {
                resp.getWriter().write(JsonUtil.toJson(admin
                    ? safeShops(supplierService.getAll()) : publicShops(supplierService.getActive())));
            }
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    /** Vue vitrine : champs d'affichage boutique uniquement (aucune PII, aucun secret). */
    private static java.util.Map<String, Object> publicShop(Supplier s) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("logo", s.getLogo());
        m.put("city", s.getCity());
        m.put("address", s.getAddress());
        m.put("description", s.getDescription());
        return m;
    }

    private static java.util.List<java.util.Map<String, Object>> publicShops(java.util.List<Supplier> list) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (list != null) for (Supplier s : list) out.add(publicShop(s));
        return out;
    }

    /** Vue admin : fiche complète SANS le hash du mot de passe (jamais exposé). */
    private static Supplier safeShop(Supplier s) {
        if (s == null) return null;
        s.setPasswordHash(null);
        return s;
    }

    private static java.util.List<Supplier> safeShops(java.util.List<Supplier> list) {
        if (list != null) for (Supplier s : list) safeShop(s);
        return list;
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
            Boolean createdActive = com.ecommerce.util.ServiceErrors.toBoolean(body.get("active"));
            s.setActive(createdActive == null || createdActive);
            s.setCommissionRate(10.0);

            String password = (String) body.get("password");
            if (password != null && !password.isEmpty()) {
                s.setPasswordHash(com.ecommerce.util.PasswordUtil.hash(password));
            }

            int id = supplierService.createSupplier(s);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true}");
        } catch (Exception e) {
            com.ecommerce.util.ServiceErrors.send(resp, e,
                "L'email est obligatoire",
                "Un compte existe déjà avec cet email",
                "Impossible de créer le compte");
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

            Integer id = com.ecommerce.util.ServiceErrors.toId(body.get("id"));
            if (id == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant fournisseur requis\"}");
                return;
            }
            Boolean active = com.ecommerce.util.ServiceErrors.toBoolean(body.get("active"));

            // Suspension/activation : mise à jour du seul statut (le dashboard
            // n'envoie que {id, active}). Sans ce chemin, les champs NULL
            // écrasaient la fiche (name NOT NULL => 500).
            if (active != null && body.get("name") == null && body.get("email") == null) {
                supplierService.updateStatus(id, active);
                try {
                    new com.ecommerce.dao.ActivityLogDAO().log(adminId,
                        active ? "SUPPLIER_ACTIVATED" : "SUPPLIER_SUSPENDED",
                        "supplier", id,
                        "Fournisseur " + (active ? "activé" : "suspendu"), req.getRemoteAddr());
                } catch (Exception ignored) {}
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            Supplier s = new Supplier();
            s.setId(id);
            s.setName((String) body.get("name"));
            s.setPhone((String) body.get("phone"));
            s.setWhatsapp((String) body.get("whatsapp"));
            s.setEmail((String) body.get("email"));
            s.setCity((String) body.get("city"));
            s.setAddress((String) body.get("address"));
            s.setActive(active == null || active);

            supplierService.updateSupplier(s);
            try {
                new com.ecommerce.dao.ActivityLogDAO().log(adminId,
                    s.isActive() ? "SUPPLIER_ACTIVATED" : "SUPPLIER_SUSPENDED",
                    "supplier", s.getId(),
                    "Fournisseur " + (s.isActive() ? "activé" : "suspendu"), req.getRemoteAddr());
            } catch (Exception ignored) {}
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            com.ecommerce.util.ServiceErrors.send(resp, e,
                "Fournisseur introuvable",
                "Nom de boutique obligatoire");
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
                resp.getWriter().write("{\"error\":\"Identifiant fournisseur requis\"}");
                return;
            }
            supplierService.deleteSupplier(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
