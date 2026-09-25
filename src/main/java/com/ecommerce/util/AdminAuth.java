package com.ecommerce.util;

import com.ecommerce.dao.AdminSessionDAO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Garde d'authentification pour les opérations d'administration.
 *
 * Les pages publiques (index) n'envoient jamais de token admin ;
 * le dashboard joint toujours {@code Authorization: Bearer <token>}.
 * Toute opération réservée à l'admin doit appeler {@link #requireAdmin}
 * avant d'agir : sans session valide, 401 + aucune modification.
 */
public final class AdminAuth {

    private AdminAuth() {}

    /**
     * @return id de l'admin si la session est valide, 0 sinon (avec 401 écrit).
     */
    public static int requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int adminId = 0;
        try {
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            if (token == null || token.isBlank()) token = req.getParameter("token");
            if (token != null && !token.isBlank()) {
                adminId = new AdminSessionDAO().validateSession(token);
            }
        } catch (Exception ignored) {
            adminId = 0;
        }
        if (adminId <= 0) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Non autorisé\"}");
        }
        return adminId;
    }
}
