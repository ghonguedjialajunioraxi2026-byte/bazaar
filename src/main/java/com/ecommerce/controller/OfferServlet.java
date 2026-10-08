package com.ecommerce.controller;

import com.ecommerce.model.Offer;
import com.ecommerce.service.OfferService;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * Offres de négociation (Phase 8). Deux rôles sur les mêmes routes :
 * - Fournisseur : Authorization Bearer (résolu serveur, jamais du navigateur).
 * - Invité : phone + cid vérifiés contre la conversation.
 */
@WebServlet("/api/offers/*")
public class OfferServlet extends HttpServlet {

    private final OfferService offerService = new OfferService();
    private final SupplierAuthService supplierAuthService = new SupplierAuthService();

    private Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return JsonUtil.fromJson(sb.toString(), Map.class);
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int num(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    /** Id fournisseur si Bearer valide, 0 sinon (repli invité). */
    private int supplierIdOf(HttpServletRequest req) {
        try {
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else token = null; // header seul (le ?token= fuit dans logs/historique)
            if (token == null || token.isBlank()) return 0;
            return supplierAuthService.validateToken(token);
        } catch (Exception e) {
            return 0;
        }
    }

    private String[] pathParts(HttpServletRequest req) {
        String pi = req.getPathInfo();
        if (pi == null || pi.equals("/")) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : pi.split("/")) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            int supplierId = supplierIdOf(req);
            if (parts.length == 0) {
                // Historique d'une conversation (propriété vérifiée côté service).
                int convId = num(req.getParameter("conversationId"), 0);
                if (convId <= 0) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "Conversation requise");
                    return;
                }
                if (supplierId > 0) {
                    // IDOR corrigé : la conversation doit appartenir au fournisseur.
                    if (new com.ecommerce.dao.ConversationDAO()
                            .findByIdScoped(supplierId, convId) == null) {
                        ApiResponse.fail(resp, 403, ErrorCodes.OFFER_ACCESS_DENIED,
                            "Conversation inaccessible");
                        return;
                    }
                    ApiResponse.ok(resp, offerService.listForConversation(convId));
                    return;
                }
                // Client : compte authentifié prioritaire (conversation du compte
                // ou invitée prouvée), sinon preuve phone+cid (invité).
                Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                    com.ecommerce.util.CustomerAuth.validateCustomer(req));
                if (session != null) {
                    new com.ecommerce.service.ConversationService().getForCustomerOrLink(
                        convId, session, req.getParameter("phone"), req.getParameter("cid"));
                } else {
                    new com.ecommerce.service.ConversationService().getForGuest(
                        convId, req.getParameter("phone"), req.getParameter("cid"));
                }
                ApiResponse.ok(resp, offerService.listForConversation(convId));
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            if (parts.length != 0) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            Map<String, Object> body = readBody(req);
            int supplierId = supplierIdOf(req);
            Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                com.ecommerce.util.CustomerAuth.validateCustomer(req));
            Offer o;
            if (supplierId > 0) {
                o = offerService.createOffer(num(body.get("conversationId"), 0),
                    num(body.get("productId"), 0), num(body.get("qty"), 1),
                    str(body.get("size")), str(body.get("color")),
                    num(body.get("unitPrice"), 0), "SUPPLIER", null, null, supplierId, session);
            } else {
                o = offerService.createOffer(num(body.get("conversationId"), 0),
                    num(body.get("productId"), 0), num(body.get("qty"), 1),
                    str(body.get("size")), str(body.get("color")),
                    num(body.get("unitPrice"), 0), "CLIENT",
                    str(body.get("phone")), str(body.get("cid")), 0, session);
            }
            ApiResponse.created(resp, o);
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handlePatch(req, resp);
            return;
        }
        super.service(req, resp);
    }

    /**
     * DELETE /api/offers/{id} — fournisseur propriétaire uniquement
     * (Bearer), offres PENDING / REJECTED uniquement (CRUD : Delete).
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            int supplierId = supplierIdOf(req);
            if (supplierId <= 0) {
                resp.setStatus(401);
                resp.getWriter().write("{\"error\":\"Non autorisé\"}");
                return;
            }
            String[] parts = pathParts(req);
            if (parts.length != 1) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            int offerId = Integer.parseInt(parts[0]);
            try {
                offerService.deleteOfferForSupplier(offerId, supplierId);
            } catch (AppException ae) {
                ApiResponse.fromException(resp, ae);
                return;
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    protected void handlePatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            if (parts.length != 2) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            int offerId = Integer.parseInt(parts[0]);
            String op = parts[1];
            int supplierId = supplierIdOf(req);
            Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                com.ecommerce.util.CustomerAuth.validateCustomer(req));
            Offer o;
            if ("accept".equals(op) || "reject".equals(op)) {
                boolean accept = "accept".equals(op);
                if (supplierId > 0) {
                    o = offerService.decide(offerId, accept, "SUPPLIER", null, null, supplierId, session);
                } else {
                    Map<String, Object> body = readBody(req);
                    o = offerService.decide(offerId, accept, "CLIENT",
                        str(body.get("phone")), str(body.get("cid")), 0, session);
                }
                ApiResponse.ok(resp, o);
                return;
            }
            if ("cancel".equals(op)) {
                if (supplierId > 0) {
                    offerService.cancel(offerId, "SUPPLIER", null, null, supplierId, session);
                } else {
                    Map<String, Object> body = readBody(req);
                    offerService.cancel(offerId, "CLIENT", str(body.get("phone")), str(body.get("cid")), 0, session);
                }
                ApiResponse.ok(resp, null);
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }
}
