package com.ecommerce.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construction homogène des réponses JSON (Phase 2).
 *
 * Format cible : {"success":bool,"data":…,"message":…,"errorCode":…}
 * Compatibilité : les champs historiques ("error", "orderId", …) sont
 * CONSERVÉS quand le frontend les consomme ; les nouveaux champs
 * s'y AJOUTENT. Ne jamais renommer/supprimer sans adapter le frontend.
 */
public final class ApiResponse {

    private ApiResponse() {}

    public static void ok(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data", data);
        resp.setStatus(200);
        resp.getWriter().write(JsonUtil.toJson(m));
    }

    public static void created(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data", data);
        resp.setStatus(201);
        resp.getWriter().write(JsonUtil.toJson(m));
    }

    /** Erreur propre : message utilisateur + code stable, SANS détail interne. */
    public static void fail(HttpServletResponse resp, int httpStatus, String code, String userMessage) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("data", null);
        m.put("message", userMessage);
        m.put("errorCode", code);
        // Champ historique conservé pour les frontends existants :
        m.put("error", userMessage);
        resp.setStatus(httpStatus);
        resp.getWriter().write(JsonUtil.toJson(m));
    }

    public static void fromException(HttpServletResponse resp, AppException e) throws IOException {
        // Cause réelle journalisable côté serveur par l'appelant ; jamais exposée ici.
        fail(resp, e.getHttpStatus(), e.getCode(), e.getMessage());
    }
}
