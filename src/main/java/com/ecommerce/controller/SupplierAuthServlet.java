package com.ecommerce.controller;

import com.ecommerce.model.Supplier;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.service.SupplierService;
import com.ecommerce.util.GoogleAuthUtil;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/supplier/login", "/api/supplier/register", "/api/supplier/google",
        "/api/supplier/google-client-id"})
public class SupplierAuthServlet extends HttpServlet {

    /**
     * Expose le Client ID Google effectif (source unique de vérité).
     * Public : le bouton Google du frontend le récupère au chargement,
     * avec repli sur la constante en dur si besoin.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (req.getRequestURI().endsWith("/api/supplier/google-client-id")) {
            resp.getWriter().write("{\"clientId\":\"" + GoogleAuthUtil.GOOGLE_CLIENT_ID + "\"}");
            return;
        }
        resp.setStatus(404);
        resp.getWriter().write("{\"success\":false,\"message\":\"Route inconnue\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        SupplierAuthService authService = new SupplierAuthService();
        SupplierService supplierService = new SupplierService();
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, String> body = JsonUtil.fromJson(sb.toString(), Map.class);

            // Connexion via Google Sign-In
            if (req.getRequestURI().endsWith("/api/supplier/google")) {
                String idToken = body.get("idToken");
                if (idToken == null) idToken = body.get("credential");
                GoogleAuthUtil.GoogleUser gu;
                try {
                    gu = GoogleAuthUtil.verify(idToken);
                } catch (Exception e) {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                Supplier gs;
                try {
                    gs = authService.loginWithGoogle(gu.sub, gu.email, gu.name, gu.picture);
                } catch (SQLException e) {
                    resp.setStatus(403);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                String gtoken = authService.createSession(gs.getId(), req.getRemoteAddr(), req.getHeader("User-Agent"));
                resp.getWriter().write(authJson(gtoken, supplierService.getProfile(gs.getId())));
                return;
            }

            // Inscription
            if (req.getRequestURI().endsWith("/api/supplier/register")) {
                String email = body.get("email");
                String password = body.get("password");
                String name = body.get("name");
                String contactName = body.get("contactName");
                String phone = body.get("phone");
                String whatsapp = body.get("whatsapp") != null ? body.get("whatsapp") : phone;
                String city = body.get("city");
                String address = body.get("address");
                String description = body.get("description");

                if (email == null || email.trim().isEmpty()
                        || password == null || password.length() < 6) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Email valide et mot de passe (min 6 caractères) requis\"}");
                    return;
                }
                if (name == null || name.trim().isEmpty()) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Le nom de la boutique est obligatoire\"}");
                    return;
                }

                Supplier s = new Supplier();
                s.setName(name.trim());
                s.setEmail(email.trim());
                s.setContactName(contactName);
                s.setPhone(phone);
                s.setWhatsapp(whatsapp);
                s.setCity(city);
                s.setAddress(address);
                s.setDescription(description);

                Supplier created;
                try {
                    created = supplierService.register(s, password);
                } catch (SQLException e) {
                    resp.setStatus(409);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }

                String token = authService.createSession(created.getId(), req.getRemoteAddr(), req.getHeader("User-Agent"));
                resp.getWriter().write(authJson(token, supplierService.getProfile(created.getId())));
                return;
            }

            String email = body.get("email");
            String password = body.get("password");

            // Anti brute-force : 5 échecs → 15 min (clé email + IP).
            String attemptKey = com.ecommerce.service.LoginAttemptService.key(
                "supplier", email, req.getRemoteAddr());
            if (com.ecommerce.service.LoginAttemptService.get().isBlocked(attemptKey)) {
                resp.setStatus(429);
                resp.getWriter().write("{\"success\":false,\"message\":\"Trop de tentatives. Réessayez dans 15 minutes.\"}");
                return;
            }

            Supplier s = authService.login(email, password);
            if (s == null) {
                com.ecommerce.service.LoginAttemptService.get().recordFailure(attemptKey);
                resp.setStatus(401);
                resp.getWriter().write("{\"success\":false,\"message\":\"Email ou mot de passe incorrect\"}");
                return;
            }
            com.ecommerce.service.LoginAttemptService.get().recordSuccess(attemptKey);

            String token = authService.createSession(s.getId(), req.getRemoteAddr(), req.getHeader("User-Agent"));
            resp.getWriter().write(authJson(token, supplierService.getProfile(s.getId())));
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Réponse d'authentification unifiée, avec indicateur de profil finalisé.
     * profileComplete=false -> le frontend redirige vers l'écran
     * "Finaliser ma boutique" (nom, ville, téléphone, adresse).
     */
    private String authJson(String token, Supplier s) {
        boolean complete = new SupplierAuthService().isProfileComplete(s);
        return "{\"success\":true,\"token\":\"" + token
                + "\",\"profileComplete\":" + complete
                + ",\"supplier\":{\"id\":" + s.getId()
                + ",\"name\":\"" + safe(s.getName())
                + "\",\"email\":\"" + safe(s.getEmail())
                + "\",\"phone\":\"" + safe(s.getPhone())
                + "\",\"city\":\"" + safe(s.getCity())
                + "\",\"address\":\"" + safe(s.getAddress())
                + "\",\"logo\":\"" + safe(s.getLogo()) + "\"}}";
    }
}
