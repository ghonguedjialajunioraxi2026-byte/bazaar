package com.ecommerce.controller;

import com.ecommerce.config.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;

/**
 * Endpoint de santé (Phase 21, préparation production).
 *
 * <p>{@code GET /api/health} → {@code {"status":"UP"}} si l'application
 * répond et que la base est joignable, {@code {"status":"DOWN"}} (HTTP 503)
 * sinon. Ne retourne jamais de secret ni de détail interne : le monitoring
 * (reverse proxy, uptime) n'a besoin que du statut et du code HTTP.</p>
 *
 * <p>Réponse mise en cache : jamais (données d'état).</p>
 */
@WebServlet("/api/health")
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        boolean dbUp = false;
        try (Connection c = DBConnection.getConnection()) {
            dbUp = c.isValid(2);
        } catch (Exception ignored) {
            // DOWN : aucun détail exposé.
        }

        if (dbUp) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"status\":\"UP\"}");
        } else {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write("{\"status\":\"DOWN\"}");
        }
    }
}
